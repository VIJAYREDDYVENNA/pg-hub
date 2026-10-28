package com.hostelmanagement.payments.online;

import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.common.Money;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.payments.Invoice;
import com.hostelmanagement.payments.InvoiceRepository;
import com.hostelmanagement.payments.Payment;
import com.hostelmanagement.payments.PaymentService;
import com.hostelmanagement.payments.PaymentService.AllocationRequest;
import com.hostelmanagement.payments.PaymentService.PaymentCommand;
import com.hostelmanagement.payments.gateway.PaymentGatewayService.GatewayPayment;
import com.hostelmanagement.payments.gateway.PaymentGatewayService.State;
import com.hostelmanagement.payments.gateway.PaymentGatewayService.WebhookEvent;
import com.hostelmanagement.payments.online.PaymentAttempt.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * The only code path that turns provider-reported money into invoice payments. Every entry point (checkout
 * confirmation, webhook, reconciliation) funnels into {@link #apply}, which runs in one transaction under a row
 * lock on the attempt (then the invoice), so duplicate or concurrent deliveries can never apply a payment twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAttemptProcessor {

    public enum Result {
        /** Money verified and applied to the invoice by this call. */
        APPLIED,
        /** The attempt had already been settled; nothing changed. */
        ALREADY_APPLIED,
        /** A non-final state was recorded (pending / failed). */
        RECORDED,
        /** The provider's amount/currency/owner did not match the attempt; nothing was applied. */
        MISMATCH,
        UNKNOWN_ORDER,
        DUPLICATE_EVENT,
        IGNORED
    }

    private final PaymentAttemptRepository attempts;
    private final PaymentWebhookEventRepository webhookEvents;
    private final InvoiceRepository invoiceRepository;
    private final PaymentService paymentService;
    private final AuditService auditService;
    private final AppClock clock;

    @Transactional
    public Result processWebhook(String provider, WebhookEvent event) {
        if (!webhookEvents.register(provider, event.eventId(), event.type(), clock.now())) {
            log.info("Webhook {} {} already processed; acknowledging duplicate", provider, event.eventId());
            return Result.DUPLICATE_EVENT;
        }
        if (event.payment() == null) {
            return Result.IGNORED;
        }
        return apply(provider, event.payment());
    }

    @Transactional
    public Result apply(String provider, GatewayPayment gp) {
        PaymentAttempt attempt = gp.providerOrderId() == null ? null
                : attempts.lockByOrder(provider, gp.providerOrderId()).orElse(null);
        if (attempt == null) {
            log.warn("Gateway payment {} refers to unknown {} order {}", gp.providerPaymentId(), provider, gp.providerOrderId());
            return Result.UNKNOWN_ORDER;
        }
        attempt.setLastCheckedAt(clock.now());
        if (attempt.getStatus() == Status.SUCCEEDED || attempt.getStatus() == Status.MISMATCH) {
            if (attempt.getStatus() == Status.SUCCEEDED && gp.state() == State.SUCCEEDED
                    && !gp.providerPaymentId().equals(attempt.getProviderPaymentId())) {
                log.error("Order {} has a second captured payment {} besides {}; needs manual review",
                        attempt.getProviderOrderId(), gp.providerPaymentId(), attempt.getProviderPaymentId());
                auditService.log(attempt.getOwnerId(), attempt.getInitiatedBy(), "ONLINE_PAYMENT_DUPLICATE_CAPTURE",
                        "PAYMENT_ATTEMPT", attempt.getId(), "Second capture " + gp.providerPaymentId());
            }
            return Result.ALREADY_APPLIED;
        }
        switch (gp.state()) {
            case PENDING -> {
                attempt.setStatus(Status.PENDING);
                attempt.setMethod(gp.method());
                return Result.RECORDED;
            }
            case FAILED -> {
                attempt.setStatus(Status.FAILED);
                attempt.setMethod(gp.method());
                attempt.setFailureReason(truncate(gp.failureReason() == null ? "Payment failed" : gp.failureReason()));
                return Result.RECORDED;
            }
            default -> {
                return applySuccess(attempt, gp);
            }
        }
    }

    /** Records that a submitted payment could not be confirmed yet (provider unreachable); reconciliation resolves it. */
    @Transactional
    public void markPending(String provider, String orderId) {
        attempts.lockByOrder(provider, orderId).ifPresent(a -> {
            if (a.getStatus() == Status.CREATED || a.getStatus() == Status.FAILED) {
                a.setStatus(Status.PENDING);
            }
            a.setLastCheckedAt(clock.now());
        });
    }

    @Transactional
    public void touch(Long attemptId) {
        attempts.findById(attemptId).ifPresent(a -> a.setLastCheckedAt(clock.now()));
    }

    private Result applySuccess(PaymentAttempt attempt, GatewayPayment gp) {
        long expectedMinor = attempt.getAmount().movePointRight(2).longValueExact();
        if (gp.amountMinor() != expectedMinor || !attempt.getCurrency().equalsIgnoreCase(gp.currency())) {
            return flag(attempt, gp, "Provider reported " + gp.amountMinor() + " " + gp.currency()
                    + " but the attempt was for " + expectedMinor + " " + attempt.getCurrency());
        }
        // Lock order is always attempt -> invoice, matching every other path that touches both.
        Invoice invoice = invoiceRepository.lockById(attempt.getInvoiceId()).orElse(null);
        if (invoice == null || !invoice.getOwnerId().equals(attempt.getOwnerId())
                || !invoice.getHostelId().equals(attempt.getHostelId())
                || !invoice.getResidentId().equals(attempt.getResidentId())) {
            return flag(attempt, gp, "Invoice no longer matches the attempt's owner, hostel or resident");
        }
        BigDecimal amount = attempt.getAmount();
        BigDecimal balance = invoice.balance();
        // If the invoice was settled meanwhile (e.g. cash), the money is still recorded; PaymentService keeps any
        // surplus as resident credit, exactly like a staff-recorded overpayment.
        List<AllocationRequest> allocations = invoice.isOpen() && balance.signum() > 0
                ? List.of(new AllocationRequest(invoice.getId(), Money.min(amount, balance))) : null;
        String note = "Online payment via " + attempt.getProvider() + (gp.method() == null ? "" : " (" + gp.method() + ")")
                + ", order " + attempt.getProviderOrderId() + (attempt.isSimulated() ? " [SIMULATED]" : "");
        Payment payment = paymentService.record(new PaymentCommand(attempt.getOwnerId(), attempt.getInitiatedBy(),
                attempt.getResidentId(), attempt.getHostelId(), invoice.getAdmissionId(), amount, clock.today(),
                "ONLINE", gp.providerPaymentId(), note, allocations, false, null));
        attempt.setStatus(Status.SUCCEEDED);
        attempt.setProviderPaymentId(gp.providerPaymentId());
        attempt.setMethod(gp.method());
        attempt.setFailureReason(null);
        attempt.setPaymentId(payment.getId());
        attempt.setCompletedAt(clock.now());
        auditService.log(attempt.getOwnerId(), attempt.getInitiatedBy(), "ONLINE_PAYMENT_VERIFIED", "PAYMENT_ATTEMPT",
                attempt.getId(), attempt.getProvider() + " " + gp.providerPaymentId() + " " + amount + " -> "
                        + payment.getReceiptNumber() + (attempt.isSimulated() ? " (simulated)" : ""));
        return Result.APPLIED;
    }

    private Result flag(PaymentAttempt attempt, GatewayPayment gp, String reason) {
        log.error("Not applying {} payment {} for attempt {}: {}", attempt.getProvider(), gp.providerPaymentId(),
                attempt.getId(), reason);
        attempt.setStatus(Status.MISMATCH);
        attempt.setFailureReason(truncate(reason));
        attempt.setCompletedAt(clock.now());
        auditService.log(attempt.getOwnerId(), attempt.getInitiatedBy(), "ONLINE_PAYMENT_MISMATCH", "PAYMENT_ATTEMPT",
                attempt.getId(), "Provider payment " + gp.providerPaymentId() + ": " + reason);
        return Result.MISMATCH;
    }

    private static String truncate(String s) {
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
