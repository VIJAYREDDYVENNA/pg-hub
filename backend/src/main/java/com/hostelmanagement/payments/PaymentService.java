package com.hostelmanagement.payments;

import com.hostelmanagement.admission.AdmissionRepository;
import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.common.Money;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.common.events.DomainEvents;
import com.hostelmanagement.resident.Resident;
import com.hostelmanagement.resident.ResidentRepository;
import com.hostelmanagement.resident.ResidentService;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Records money actually received. A payment is only ever created from an explicit staff action (never from a
 * reminder or message being sent). Amounts are applied to invoices oldest-due-first unless allocations are given;
 * any remainder becomes resident credit that is applied to future invoices automatically.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    public static final String METHODS = "CASH|UPI|BANK_TRANSFER|CARD|OTHER";

    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final InvoiceRepository invoiceRepository;
    private final DepositRepository depositRepository;
    private final RefundRepository refundRepository;
    private final AdmissionRepository admissionRepository;
    private final ResidentRepository residentRepository;
    private final ResidentService residentService;
    private final InvoiceService invoiceService;
    private final NumberSequenceService sequences;
    private final HostelAccessService hostelAccess;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final AppClock clock;

    public record AllocationRequest(@NotNull Long invoiceId, @NotNull @DecimalMin("0.01") BigDecimal amount) {
    }

    public record RecordPaymentRequest(@NotNull Long residentId,
                                       @NotNull @DecimalMin("0.01") BigDecimal amount,
                                       @NotNull LocalDate paymentDate,
                                       @NotNull @Pattern(regexp = METHODS) String method,
                                       @Size(max = 100) String transactionReference,
                                       @Size(max = 500) String notes,
                                       List<@Valid AllocationRequest> allocations,
                                       boolean sendWhatsApp,
                                       /** Optional retry key: resubmitting the same key returns the original payment instead of a second one. */
                                       @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "Use letters, digits, - or _") String idempotencyKey) {
    }

    public record PaymentCommand(Long ownerId, Long userId, Long residentId, Long hostelId, Long admissionId,
                                 BigDecimal amount, LocalDate paymentDate, String method, String reference,
                                 String notes, List<AllocationRequest> allocations, boolean sendWhatsApp,
                                 String idempotencyKey) {
    }

    public record RefundRequest(@NotNull Long residentId,
                                @NotNull @Pattern(regexp = "DEPOSIT|ADVANCE") String refundType,
                                Long admissionId,
                                @NotNull @DecimalMin("0.01") BigDecimal amount,
                                @NotNull LocalDate refundDate,
                                @NotNull @Pattern(regexp = METHODS) String method,
                                @Size(max = 100) String transactionReference,
                                @NotBlank @Size(max = 500) String reason) {
    }

    public record DepositAdjustRequest(@NotNull Long admissionId, @NotNull @DecimalMin("0.01") BigDecimal amount,
                                       @Size(max = 500) String notes) {
    }

    @Transactional
    public Payment collect(RecordPaymentRequest req) {
        AuthUser user = CurrentUser.get();
        Resident r = residentService.getEntity(req.residentId());
        if (req.paymentDate().isAfter(clock.today())) {
            throw ApiException.badRequest("Payment date cannot be in the future");
        }
        String key = blank(req.idempotencyKey());
        if (key != null) {
            var existing = paymentRepository.findByOwnerIdAndIdempotencyKey(user.ownerId(), key);
            if (existing.isPresent()) {
                return replayOf(existing.get(), r, req);
            }
        }
        Long admissionId = admissionRepository.findFirstByResidentIdAndStatus(r.getId(), "ACTIVE")
                .map(a -> a.getId()).orElse(null);
        return record(new PaymentCommand(user.ownerId(), user.userId(), r.getId(), r.getHostelId(), admissionId,
                Money.of(req.amount()), req.paymentDate(), req.method(), req.transactionReference(), req.notes(),
                req.allocations(), req.sendWhatsApp(), req.idempotencyKey()));
    }

    /** A repeated submission is only a replay if it describes the same payment; otherwise the key was reused by mistake. */
    private Payment replayOf(Payment existing, Resident r, RecordPaymentRequest req) {
        if (!existing.getResidentId().equals(r.getId()) || existing.getAmount().compareTo(Money.of(req.amount())) != 0) {
            throw ApiException.conflict("This idempotency key was already used for a different payment");
        }
        return existing;
    }

    /** Used by the controller when two identical submissions race: the loser returns the winner's payment. */
    @Transactional(readOnly = true)
    public java.util.Optional<Payment> findByIdempotencyKey(String key) {
        String k = blank(key);
        return k == null ? java.util.Optional.empty()
                : paymentRepository.findByOwnerIdAndIdempotencyKey(CurrentUser.get().ownerId(), k);
    }

    @Transactional
    public Payment record(PaymentCommand cmd) {
        if (!Money.isPositive(cmd.amount())) {
            throw ApiException.badRequest("Amount must be greater than zero");
        }
        Payment p = new Payment();
        p.setOwnerId(cmd.ownerId());
        p.setHostelId(cmd.hostelId());
        p.setResidentId(cmd.residentId());
        p.setAdmissionId(cmd.admissionId());
        p.setReceiptNumber(sequences.next(cmd.ownerId(), "RCP", cmd.paymentDate().getYear()));
        p.setAmount(Money.of(cmd.amount()));
        p.setUnallocatedAmount(Money.of(cmd.amount()));
        p.setPaymentDate(cmd.paymentDate());
        p.setMethod(cmd.method());
        p.setTransactionReference(blank(cmd.reference()));
        p.setIdempotencyKey(blank(cmd.idempotencyKey()));
        p.setNotes(blank(cmd.notes()));
        p.setRecordedBy(cmd.userId());
        paymentRepository.save(p);

        LocalDate today = clock.today();
        boolean adjustment = "DEPOSIT_ADJUSTMENT".equals(cmd.method());
        if (cmd.allocations() != null && !cmd.allocations().isEmpty()) {
            BigDecimal sum = Money.ZERO;
            var seen = new HashSet<Long>();
            for (AllocationRequest ar : cmd.allocations()) {
                if (!seen.add(ar.invoiceId())) {
                    throw ApiException.badRequest("An invoice appears more than once in the allocation");
                }
                Invoice inv = invoiceRepository.lockById(ar.invoiceId())
                        .filter(i -> i.getResidentId().equals(cmd.residentId()))
                        .orElseThrow(() -> ApiException.badRequest("Invoice does not belong to this resident"));
                BigDecimal amt = Money.of(ar.amount());
                if (!inv.isOpen()) {
                    throw ApiException.badRequest("Invoice " + inv.getInvoiceNumber() + " is not open for payment");
                }
                if (amt.compareTo(inv.balance()) > 0) {
                    throw ApiException.badRequest("Allocation exceeds the balance of " + inv.getInvoiceNumber());
                }
                sum = sum.add(amt);
                if (sum.compareTo(p.getAmount()) > 0) {
                    throw ApiException.badRequest("Allocations exceed the payment amount");
                }
                invoiceService.allocate(p, inv, amt, today);
            }
        } else {
            List<String> types = adjustment ? List.of("RENT", "OTHER") : List.of("DEPOSIT", "RENT", "OTHER");
            for (Invoice inv : invoiceRepository.lockOpenForResident(cmd.residentId(), Invoice.OPEN_STATUSES, types)) {
                if (p.getUnallocatedAmount().signum() <= 0) {
                    break;
                }
                BigDecimal x = Money.min(p.getUnallocatedAmount(), inv.balance());
                if (x.signum() > 0) {
                    invoiceService.allocate(p, inv, x, today);
                }
            }
        }
        if (adjustment && p.getUnallocatedAmount().signum() > 0) {
            throw ApiException.badRequest("Deposit adjustment exceeds the resident's outstanding rent and charges");
        }
        Resident r = residentRepository.findById(cmd.residentId()).orElseThrow();
        auditService.log(cmd.ownerId(), cmd.userId(), "PAYMENT_RECORDED", "PAYMENT", p.getId(),
                p.getReceiptNumber() + " " + p.getAmount() + " " + p.getMethod() + " from " + r.getFullName());
        if (!adjustment) {
            events.publishEvent(new DomainEvents.PaymentRecorded(cmd.ownerId(), cmd.hostelId(), p.getId(),
                    r.getId(), r.getFullName(), p.getAmount(), p.getReceiptNumber(), p.getMethod(), p.getPaymentDate(),
                    cmd.sendWhatsApp(), cmd.userId()));
        }
        return p;
    }

    public Payment getAccessible(Long id) {
        Payment p = paymentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Payment"));
        if (!p.getOwnerId().equals(CurrentUser.get().ownerId())) {
            throw ApiException.notFound("Payment");
        }
        hostelAccess.checkEntity(p.getHostelId(), "Payment");
        return p;
    }

    /** Reverses a payment (e.g. bounced transfer). History is kept; invoice balances are restored. */
    @Transactional
    public Payment reverse(Long id, String reason) {
        AuthUser user = CurrentUser.get();
        getAccessible(id);
        Payment p = paymentRepository.lockById(id).orElseThrow();
        if (!"RECEIVED".equals(p.getStatus())) {
            throw ApiException.conflict("This payment has already been reversed");
        }
        LocalDate today = clock.today();
        List<PaymentAllocation> allocations = allocationRepository.findByPaymentId(id);
        // amount = applied to invoices + still-held credit + advance already refunded. If part was refunded to the
        // resident, reversing would silently turn that refund into money the system has lost track of.
        BigDecimal allocated = allocations.stream().map(PaymentAllocation::getAmount).reduce(Money.ZERO, BigDecimal::add);
        BigDecimal alreadyRefunded = p.getAmount().subtract(allocated).subtract(p.getUnallocatedAmount());
        if (alreadyRefunded.signum() > 0) {
            throw ApiException.conflict("Rs. " + alreadyRefunded.toPlainString() + " of this payment was already refunded "
                    + "to the resident as advance credit, so it cannot be reversed. Settle that refund first.");
        }
        for (PaymentAllocation pa : allocations) {
            Invoice inv = invoiceRepository.lockById(pa.getInvoiceId()).orElseThrow();
            inv.setAmountPaid(inv.getAmountPaid().subtract(pa.getAmount()));
            if ("PAID".equals(inv.getStatus()) || inv.isOpen()) {
                inv.setStatus("PENDING");
                InvoiceService.refreshStatus(inv, today);
            }
            if ("DEPOSIT".equals(inv.getInvoiceType())) {
                depositRepository.findByInvoiceId(inv.getId()).ifPresent(d -> {
                    d.setAmountReceived(d.getAmountReceived().subtract(pa.getAmount()));
                    if (d.held().signum() < 0) {
                        throw ApiException.conflict("Part of this deposit was already refunded or adjusted, so this payment "
                                + "cannot be reversed. Reverse the later deposit adjustment first.");
                    }
                    d.refreshStatus();
                });
            }
        }
        if ("DEPOSIT_ADJUSTMENT".equals(p.getMethod()) && p.getAdmissionId() != null) {
            // The adjustment moved money out of the deposit into rent; reversing the payment must put it back
            depositRepository.lockByAdmissionId(p.getAdmissionId()).ifPresent(d -> {
                d.setAmountAdjusted(d.getAmountAdjusted().subtract(p.getAmount()));
                if (d.getAmountAdjusted().signum() < 0) {
                    throw ApiException.conflict("The deposit records no adjustment of this size; it cannot be reversed");
                }
                d.refreshStatus();
            });
        }
        p.setStatus("REVERSED");
        p.setUnallocatedAmount(Money.ZERO);
        p.setReversedAt(Instant.now());
        p.setReversedBy(user.userId());
        p.setReversalReason(reason);
        auditService.log("PAYMENT_REVERSED", "PAYMENT", id, p.getReceiptNumber() + ": " + reason);
        return p;
    }

    @Transactional
    public Refund refund(RefundRequest req) {
        AuthUser user = CurrentUser.get();
        Resident r = residentService.getEntity(req.residentId());
        if (req.refundDate().isAfter(clock.today())) {
            throw ApiException.badRequest("Refund date cannot be in the future");
        }
        BigDecimal amount = Money.of(req.amount());
        Refund refund = new Refund();
        refund.setOwnerId(user.ownerId());
        refund.setHostelId(r.getHostelId());
        refund.setResidentId(r.getId());
        refund.setRefundType(req.refundType());
        refund.setAmount(amount);
        refund.setRefundDate(req.refundDate());
        refund.setMethod(req.method());
        refund.setTransactionReference(blank(req.transactionReference()));
        refund.setReason(req.reason());
        refund.setRecordedBy(user.userId());
        if ("DEPOSIT".equals(req.refundType())) {
            if (req.admissionId() == null) {
                throw ApiException.badRequest("Select the admission whose deposit is being refunded");
            }
            Deposit d = depositRepository.lockByAdmissionId(req.admissionId())
                    .filter(x -> x.getResidentId().equals(r.getId()))
                    .orElseThrow(() -> ApiException.badRequest("No deposit found for this admission"));
            if (amount.compareTo(d.held()) > 0) {
                throw ApiException.badRequest("Refund exceeds the deposit currently held (" + d.held() + ")");
            }
            d.setAmountRefunded(d.getAmountRefunded().add(amount));
            d.refreshStatus();
            refund.setDepositId(d.getId());
        } else {
            List<Payment> credits = paymentRepository.lockWithCredit(r.getId());
            BigDecimal available = credits.stream().map(Payment::getUnallocatedAmount).reduce(Money.ZERO, BigDecimal::add);
            if (amount.compareTo(available) > 0) {
                throw ApiException.badRequest("Refund exceeds the resident's advance credit (" + available + ")");
            }
            BigDecimal remaining = amount;
            for (Payment p : new ArrayList<>(credits).reversed()) {
                if (remaining.signum() <= 0) {
                    break;
                }
                BigDecimal x = Money.min(remaining, p.getUnallocatedAmount());
                p.setUnallocatedAmount(p.getUnallocatedAmount().subtract(x));
                remaining = remaining.subtract(x);
            }
        }
        refundRepository.save(refund);
        auditService.log("REFUND_RECORDED", "REFUND", refund.getId(),
                req.refundType() + " " + amount + " to " + r.getFullName() + ": " + req.reason());
        return refund;
    }

    /** Uses part of the held deposit to settle outstanding rent/charges (typically at checkout). */
    @Transactional
    public Payment adjustDeposit(DepositAdjustRequest req) {
        AuthUser user = CurrentUser.get();
        var admission = admissionRepository.findById(req.admissionId())
                .orElseThrow(() -> ApiException.notFound("Admission"));
        hostelAccess.checkEntity(admission.getHostelId(), "Admission");
        Deposit d = depositRepository.lockByAdmissionId(admission.getId())
                .orElseThrow(() -> ApiException.badRequest("No deposit found for this admission"));
        BigDecimal amount = Money.of(req.amount());
        if (amount.compareTo(d.held()) > 0) {
            throw ApiException.badRequest("Adjustment exceeds the deposit currently held (" + d.held() + ")");
        }
        d.setAmountAdjusted(d.getAmountAdjusted().add(amount));
        d.refreshStatus();
        return record(new PaymentCommand(user.ownerId(), user.userId(), admission.getResidentId(),
                admission.getHostelId(), admission.getId(), amount, clock.today(), "DEPOSIT_ADJUSTMENT", null,
                req.notes() == null ? "Adjusted from security deposit" : req.notes(), null, false, null));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
