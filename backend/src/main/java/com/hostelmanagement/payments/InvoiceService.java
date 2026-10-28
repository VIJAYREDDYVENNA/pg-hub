package com.hostelmanagement.payments;

import com.hostelmanagement.admission.Admission;
import com.hostelmanagement.admission.AdmissionRepository;
import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.common.Money;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.owner.OwnerSettings;
import com.hostelmanagement.owner.OwnerSettingsService;
import com.hostelmanagement.resident.Resident;
import com.hostelmanagement.resident.ResidentService;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Invoice lifecycle: rent generation per billing cycle, deposits, manual charges, discounts, late fees,
 * waivers and cancellation. Totals are always recomputed from line items.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final InvoiceRepository invoiceRepository;
    private final DepositRepository depositRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final AdmissionRepository admissionRepository;
    private final NumberSequenceService sequences;
    private final OwnerSettingsService settingsService;
    private final ResidentService residentService;
    private final HostelAccessService hostelAccess;
    private final AuditService auditService;
    private final AppClock clock;

    public record ManualInvoiceRequest(@NotNull Long residentId,
                                       @NotBlank @Size(max = 255) String description,
                                       @NotNull @DecimalMin("0.01") BigDecimal amount,
                                       @NotNull LocalDate dueDate,
                                       @Size(max = 500) String notes) {
    }

    public record AdjustmentRequest(@NotNull @Pattern(regexp = "DISCOUNT|LATE_FEE|CHARGE") String type,
                                    @NotNull @DecimalMin("0.01") BigDecimal amount,
                                    @NotBlank @Size(max = 255) String description) {
    }

    // ------------------------------------------------------------------ generation

    @Transactional
    public Invoice createDepositInvoice(Admission a, Long ownerId, Long userId) {
        if (!Money.isPositive(a.getSecurityDeposit())) {
            return null;
        }
        LocalDate today = clock.today();
        Invoice inv = newInvoice(ownerId, a.getHostelId(), a.getResidentId(), a.getId(), "DEPOSIT", userId, today);
        inv.setIssueDate(a.getAdmissionDate().isBefore(today) ? a.getAdmissionDate() : today);
        inv.setDueDate(a.getAdmissionDate());
        inv.addItem("DEPOSIT", "Security deposit", Money.of(a.getSecurityDeposit()));
        recalculate(inv);
        invoiceRepository.save(inv);
        Deposit d = new Deposit();
        d.setHostelId(a.getHostelId());
        d.setResidentId(a.getResidentId());
        d.setAdmissionId(a.getId());
        d.setInvoiceId(inv.getId());
        d.setAmountRequired(Money.of(a.getSecurityDeposit()));
        d.setAmountReceived(Money.ZERO);
        d.setAmountRefunded(Money.ZERO);
        d.setAmountAdjusted(Money.ZERO);
        d.setStatus("PENDING");
        depositRepository.save(d);
        return inv;
    }

    /**
     * Creates rent invoices for every billing period of the admission that has started on or before {@code upTo}
     * and has not been invoiced yet. Periods are anchored on the admission date (e.g. 15 Jan - 14 Feb).
     */
    @Transactional
    public List<Invoice> generateRentInvoices(Admission a, Long ownerId, LocalDate upTo, Long userId) {
        List<Invoice> created = new ArrayList<>();
        if (!"ACTIVE".equals(a.getStatus())) {
            return created;
        }
        OwnerSettings settings = settingsService.forOwner(ownerId);
        Set<LocalDate> existing = new HashSet<>(invoiceRepository.findRentPeriodStarts(a.getId()));
        int cycle = Math.max(1, a.getCycleMonths());
        LocalDate today = clock.today();
        for (int k = 0; ; k++) {
            LocalDate start = a.getAdmissionDate().plusMonths((long) k * cycle);
            if (start.isAfter(upTo)) {
                break;
            }
            if (existing.contains(start)) {
                continue;
            }
            LocalDate end = a.getAdmissionDate().plusMonths((long) (k + 1) * cycle).minusDays(1);
            Invoice inv = newInvoice(ownerId, a.getHostelId(), a.getResidentId(), a.getId(), "RENT", userId, today);
            inv.setPeriodStart(start);
            inv.setPeriodEnd(end);
            // Backdated periods (e.g. admission recorded late) are issued on the period start date
            inv.setIssueDate(start.isBefore(today) ? start : today);
            inv.setDueDate(start.plusDays(settings.getPaymentGraceDays()));
            inv.addItem("RENT", "Rent " + PERIOD_FMT.format(start) + " - " + PERIOD_FMT.format(end),
                    Money.of(a.getMonthlyRent().multiply(BigDecimal.valueOf(cycle))));
            recalculate(inv);
            refreshStatus(inv, today);
            invoiceRepository.save(inv);
            created.add(inv);
        }
        if (!created.isEmpty()) {
            applyResidentCredit(a.getResidentId());
        }
        return created;
    }

    /** Generates due rent for all active admissions in the given hostels (manual trigger / scheduler). */
    @Transactional
    public int generateDueRent(List<Long> hostelIds, Long ownerId, Long userId) {
        int count = 0;
        LocalDate today = clock.today();
        for (Admission a : admissionRepository.findByStatusAndHostelIdIn("ACTIVE", hostelIds)) {
            count += generateRentInvoices(a, ownerId, today, userId).size();
        }
        return count;
    }

    @Transactional
    public int generateDueRentForCurrentUser(Long hostelId) {
        AuthUser user = CurrentUser.get();
        int n = generateDueRent(hostelAccess.scope(hostelId), user.ownerId(), user.userId());
        auditService.log("RENT_INVOICES_GENERATED", "INVOICE", null, n + " invoice(s)");
        return n;
    }

    @Transactional
    public Invoice createManual(ManualInvoiceRequest req) {
        AuthUser user = CurrentUser.get();
        Resident r = residentService.getEntity(req.residentId());
        Long admissionId = admissionRepository.findFirstByResidentIdAndStatus(r.getId(), "ACTIVE")
                .map(Admission::getId).orElse(null);
        Invoice inv = newInvoice(user.ownerId(), r.getHostelId(), r.getId(), admissionId, "OTHER", user.userId(),
                clock.today());
        inv.setDueDate(req.dueDate());
        inv.setNotes(req.notes());
        inv.addItem("CHARGE", req.description().trim(), Money.of(req.amount()));
        recalculate(inv);
        refreshStatus(inv, clock.today());
        invoiceRepository.save(inv);
        applyResidentCredit(r.getId());
        auditService.log("INVOICE_CREATED", "INVOICE", inv.getId(), inv.getInvoiceNumber() + " " + req.amount());
        return inv;
    }

    // ------------------------------------------------------------------ adjustments

    public Invoice getAccessible(Long id) {
        Invoice inv = invoiceRepository.findById(id).orElseThrow(() -> ApiException.notFound("Invoice"));
        if (!inv.getOwnerId().equals(CurrentUser.get().ownerId())) {
            throw ApiException.notFound("Invoice");
        }
        hostelAccess.checkEntity(inv.getHostelId(), "Invoice");
        return inv;
    }

    private Invoice lockAccessible(Long id) {
        getAccessible(id);
        return invoiceRepository.lockById(id).orElseThrow(() -> ApiException.notFound("Invoice"));
    }

    @Transactional
    public Invoice addAdjustment(Long id, AdjustmentRequest req) {
        Invoice inv = lockAccessible(id);
        if (!inv.isOpen() && !"PAID".equals(inv.getStatus())) {
            throw ApiException.conflict("Only open or paid invoices can be adjusted");
        }
        BigDecimal amount = Money.of(req.amount());
        if ("DISCOUNT".equals(req.type()) && amount.compareTo(inv.balance()) > 0) {
            throw ApiException.badRequest("Discount cannot exceed the outstanding balance of " + inv.balance());
        }
        inv.addItem(req.type(), req.description().trim(), amount);
        recalculate(inv);
        refreshStatus(inv, clock.today());
        auditService.log("INVOICE_ADJUSTED", "INVOICE", id, req.type() + " " + amount + ": " + req.description());
        if (!"DISCOUNT".equals(req.type())) {
            applyResidentCredit(inv.getResidentId());
        }
        return inv;
    }

    @Transactional
    public Invoice waive(Long id, String reason) {
        Invoice inv = lockAccessible(id);
        if (!inv.isOpen()) {
            throw ApiException.conflict("Only unpaid or partially paid invoices can be waived");
        }
        inv.setStatus("WAIVED");
        inv.setNotes(appendNote(inv.getNotes(), "Waived (balance " + inv.balance() + "): " + reason));
        auditService.log("INVOICE_WAIVED", "INVOICE", id, "balance " + inv.balance() + "; " + reason);
        return inv;
    }

    @Transactional
    public Invoice cancel(Long id, String reason) {
        Invoice inv = lockAccessible(id);
        if (!inv.isOpen() || inv.getAmountPaid().signum() > 0) {
            throw ApiException.conflict("Only invoices without payments can be cancelled. Reverse the payments first.");
        }
        inv.setStatus("CANCELLED");
        inv.setNotes(appendNote(inv.getNotes(), "Cancelled: " + reason));
        auditService.log("INVOICE_CANCELLED", "INVOICE", id, reason);
        return inv;
    }

    /** At checkout: unpaid rent invoices for periods starting after the checkout date are cancelled. */
    @Transactional
    public int cancelUnpaidRentAfter(Admission a, LocalDate checkoutDate) {
        int n = 0;
        for (Invoice inv : invoiceRepository.findRentAfter(a.getId(), checkoutDate, Invoice.OPEN_STATUSES)) {
            if (inv.getAmountPaid().signum() == 0) {
                inv.setStatus("CANCELLED");
                inv.setNotes(appendNote(inv.getNotes(), "Cancelled automatically at checkout"));
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ credit & status

    /** Applies unallocated resident credit (advances / overpayments) to open invoices, oldest due first. */
    @Transactional
    public void applyResidentCredit(Long residentId) {
        List<Payment> credits = paymentRepository.lockWithCredit(residentId);
        if (credits.isEmpty()) {
            return;
        }
        List<Invoice> open = invoiceRepository.lockOpenForResident(residentId, Invoice.OPEN_STATUSES,
                List.of("DEPOSIT", "RENT", "OTHER"));
        LocalDate today = clock.today();
        for (Payment p : credits) {
            for (Invoice inv : open) {
                if (p.getUnallocatedAmount().signum() <= 0) {
                    break;
                }
                if (!inv.isOpen() || inv.balance().signum() <= 0) {
                    continue;
                }
                BigDecimal x = Money.min(p.getUnallocatedAmount(), inv.balance());
                allocate(p, inv, x, today);
            }
        }
    }

    /** Applies {@code amount} of payment {@code p} to invoice {@code inv}. Caller holds locks on both. */
    void allocate(Payment p, Invoice inv, BigDecimal amount, LocalDate today) {
        PaymentAllocation pa = new PaymentAllocation();
        pa.setPaymentId(p.getId());
        pa.setInvoiceId(inv.getId());
        pa.setAmount(amount);
        allocationRepository.save(pa);
        inv.setAmountPaid(inv.getAmountPaid().add(amount));
        refreshStatus(inv, today);
        p.setUnallocatedAmount(p.getUnallocatedAmount().subtract(amount));
        if ("DEPOSIT".equals(inv.getInvoiceType())) {
            depositRepository.findByInvoiceId(inv.getId()).ifPresent(d -> {
                d.setAmountReceived(d.getAmountReceived().add(amount));
                d.refreshStatus();
            });
        }
    }

    public static void recalculate(Invoice inv) {
        BigDecimal subtotal = Money.ZERO, discount = Money.ZERO, late = Money.ZERO;
        for (InvoiceItem item : inv.getItems()) {
            switch (item.getItemType()) {
                case "DISCOUNT" -> discount = discount.add(item.getAmount());
                case "LATE_FEE" -> late = late.add(item.getAmount());
                default -> subtotal = subtotal.add(item.getAmount());
            }
        }
        inv.setSubtotal(subtotal);
        inv.setDiscountAmount(discount);
        inv.setLateFeeAmount(late);
        BigDecimal total = subtotal.subtract(discount).add(late);
        if (total.signum() < 0) {
            throw ApiException.badRequest("Invoice total cannot be negative");
        }
        if (inv.getAmountPaid() != null && total.compareTo(inv.getAmountPaid()) < 0) {
            throw ApiException.badRequest("Invoice total cannot be less than the amount already paid");
        }
        inv.setTotalAmount(total);
    }

    /** Derives PENDING / PARTIALLY_PAID / PAID / OVERDUE. WAIVED and CANCELLED are terminal. */
    public static void refreshStatus(Invoice inv, LocalDate today) {
        if ("WAIVED".equals(inv.getStatus()) || "CANCELLED".equals(inv.getStatus())) {
            return;
        }
        BigDecimal balance = inv.balance();
        if (balance.signum() <= 0) {
            inv.setStatus("PAID");
        } else if (inv.getDueDate().isBefore(today)) {
            inv.setStatus("OVERDUE");
        } else if (inv.getAmountPaid().signum() > 0) {
            inv.setStatus("PARTIALLY_PAID");
        } else {
            inv.setStatus("PENDING");
        }
    }

    private Invoice newInvoice(Long ownerId, Long hostelId, Long residentId, Long admissionId, String type,
                               Long userId, LocalDate today) {
        Invoice inv = new Invoice();
        inv.setOwnerId(ownerId);
        inv.setHostelId(hostelId);
        inv.setResidentId(residentId);
        inv.setAdmissionId(admissionId);
        inv.setInvoiceType(type);
        inv.setInvoiceNumber(sequences.next(ownerId, "INV", today.getYear()));
        inv.setIssueDate(today);
        inv.setAmountPaid(Money.ZERO);
        inv.setStatus("PENDING");
        inv.setCreatedBy(userId);
        return inv;
    }

    static String appendNote(String existing, String note) {
        String combined = existing == null || existing.isBlank() ? note : existing + "\n" + note;
        return combined.length() > 500 ? combined.substring(combined.length() - 500) : combined;
    }
}
