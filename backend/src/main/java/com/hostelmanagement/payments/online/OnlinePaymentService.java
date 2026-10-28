package com.hostelmanagement.payments.online;

import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.common.Money;
import com.hostelmanagement.config.AppProperties;
import com.hostelmanagement.payments.Invoice;
import com.hostelmanagement.payments.InvoiceRepository;
import com.hostelmanagement.payments.InvoiceService;
import com.hostelmanagement.payments.PaymentRepository;
import com.hostelmanagement.payments.gateway.GatewayException;
import com.hostelmanagement.payments.gateway.PaymentGatewayService;
import com.hostelmanagement.payments.gateway.PaymentGatewayService.CreatedOrder;
import com.hostelmanagement.payments.gateway.PaymentGatewayService.GatewayPayment;
import com.hostelmanagement.payments.gateway.PaymentGatewayService.OrderRequest;
import com.hostelmanagement.payments.gateway.PaymentGatewayService.State;
import com.hostelmanagement.payments.gateway.PaymentGatewayService.WebhookEvent;
import com.hostelmanagement.payments.online.OnlinePaymentDtos.AttemptDto;
import com.hostelmanagement.payments.online.OnlinePaymentDtos.GatewayConfigDto;
import com.hostelmanagement.payments.online.OnlinePaymentDtos.OrderDto;
import com.hostelmanagement.payments.online.PaymentAttempt.Status;
import com.hostelmanagement.payments.online.PaymentAttemptProcessor.Result;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates gateway checkouts for invoices. It validates and authorises, talks to the active
 * {@link PaymentGatewayService}, and hands every provider result to {@link PaymentAttemptProcessor}, which is the only
 * code that changes balances. This class holds no transaction while calling the provider.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlinePaymentService {

    /** A submitted payment blocks a new checkout for the same invoice for this long (see refresh/reconcile). */
    private static final Duration PENDING_BLOCK = Duration.ofHours(24);
    private static final String RECEIPT_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentGatewayService gateway;
    private final AppProperties props;
    private final PaymentAttemptRepository attempts;
    private final PaymentAttemptProcessor processor;
    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final HostelAccessService hostelAccess;
    private final AppClock clock;
    private final com.hostelmanagement.paymentmethods.PaymentMethodService paymentMethods;
    private final com.hostelmanagement.subscription.EntitlementService entitlements;

    // ------------------------------------------------------------------ configuration view

    public GatewayConfigDto config() {
        var caps = gateway.capabilities();
        return new GatewayConfigDto(gateway.providerCode(), gateway.mode(), gateway.isSimulated(), props.payment().currency(),
                Money.of(props.payment().minPartialAmount()), caps.partialPayments(), caps.webhooks(),
                caps.paymentMethods());
    }

    // ------------------------------------------------------------------ starting a checkout

    /** Staff-initiated checkout. Access is checked against the caller's owner and assigned hostels. */
    public OrderDto createOrderForStaff(Long invoiceId, BigDecimal amount) {
        AuthUser user = CurrentUser.get();
        Invoice invoice = invoiceService.getAccessible(invoiceId);
        return createOrder(invoice, amount, user.userId(), "STAFF", null);
    }

    /** Resident checkout through a valid pay link. The link, not the request, decides which invoice is paid. */
    public OrderDto createOrderForLink(PaymentLink link, BigDecimal amount) {
        Invoice invoice = invoiceRepository.findById(link.getInvoiceId()).orElseThrow(() -> ApiException.notFound("Invoice"));
        return createOrder(invoice, amount, link.getCreatedBy(), "LINK", link.getId());
    }

    private OrderDto createOrder(Invoice invoice, BigDecimal requested, Long initiatedBy, String via, Long linkId) {
        entitlements.require(invoice.getOwnerId(), com.hostelmanagement.subscription.Features.ONLINE_PAYMENTS);
        paymentMethods.requireOnlineAccepted(invoice.getOwnerId(), invoice.getHostelId());
        BigDecimal amount = Money.of(requested);
        validateAmount(invoice, amount);
        Instant now = clock.now();
        if (attempts.existsByInvoiceIdAndStatusAndCreatedAtAfter(invoice.getId(), Status.PENDING, now.minus(PENDING_BLOCK))) {
            throw ApiException.conflict("A payment for this invoice is awaiting confirmation from the bank. "
                    + "Check its status before starting another payment.");
        }
        String currency = props.payment().currency();
        OrderRequest request = new OrderRequest(receiptFor(invoice), toMinor(amount), currency,
                "Invoice " + invoice.getInvoiceNumber(), orderNotes(invoice));
        CreatedOrder order = callGateway(() -> gateway.createOrder(request));
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setOwnerId(invoice.getOwnerId());
        attempt.setHostelId(invoice.getHostelId());
        attempt.setInvoiceId(invoice.getId());
        attempt.setResidentId(invoice.getResidentId());
        attempt.setProvider(gateway.providerCode());
        attempt.setProviderOrderId(order.providerOrderId());
        attempt.setAmount(amount);
        attempt.setCurrency(currency);
        attempt.setInitiatedBy(initiatedBy);
        attempt.setInitiatedVia(via);
        attempt.setPaymentLinkId(linkId);
        attempt.setSimulated(gateway.isSimulated());
        attempts.save(attempt);
        return new OrderDto(attempt.getId(), attempt.getProvider(), attempt.isSimulated(), amount, currency, order.checkout());
    }

    private void validateAmount(Invoice invoice, BigDecimal amount) {
        if (!invoice.isOpen() || invoice.balance().signum() <= 0) {
            throw ApiException.conflict("This invoice has no outstanding balance");
        }
        BigDecimal balance = invoice.balance();
        if (amount.signum() <= 0) {
            throw ApiException.badRequest("Amount must be greater than zero");
        }
        if (amount.compareTo(balance) > 0) {
            throw ApiException.badRequest("Amount cannot exceed the outstanding balance of " + balance.toPlainString());
        }
        if (amount.compareTo(balance) < 0) {
            if (!gateway.capabilities().partialPayments()) {
                throw ApiException.badRequest("Partial payments are not supported by the payment provider");
            }
            BigDecimal min = Money.of(props.payment().minPartialAmount());
            if (amount.compareTo(min) < 0) {
                throw ApiException.badRequest("A partial payment must be at least " + min.toPlainString()
                        + " (or pay the full balance of " + balance.toPlainString() + ")");
            }
        }
    }

    // ------------------------------------------------------------------ confirming a checkout

    /**
     * Verifies the browser's checkout result with the provider and applies it. The browser's claim of success is
     * never used directly; an unverifiable result leaves the attempt untouched.
     */
    public AttemptDto confirm(PaymentAttempt attempt, Map<String, String> fields) {
        GatewayPayment payment;
        try {
            payment = gateway.verifyCheckout(fields, attempt.getProviderOrderId());
        } catch (GatewayException e) {
            switch (e.getKind()) {
                case INVALID_SIGNATURE -> {
                    log.warn("Rejected unverifiable checkout result for attempt {}: {}", attempt.getId(), e.getMessage());
                    throw ApiException.badRequest("We could not verify this payment. If money was deducted it will be "
                            + "reconciled automatically; check the payment status in a few minutes.");
                }
                case UNAVAILABLE -> {
                    // The user did submit a payment; keep it open so the reconciler resolves it.
                    processor.markPending(attempt.getProvider(), attempt.getProviderOrderId());
                    return view(reload(attempt.getId()));
                }
                default -> throw translate(e);
            }
        }
        processor.apply(attempt.getProvider(), payment);
        return view(reload(attempt.getId()));
    }

    /** Asks the provider what happened to an attempt (used by the "check status" button and the reconciler). */
    public AttemptDto refresh(PaymentAttempt attempt) {
        reconcile(attempt.getId());
        return view(reload(attempt.getId()));
    }

    /** Applies the best-known provider outcome for an attempt. Safe to call repeatedly. */
    public Result reconcile(Long attemptId) {
        PaymentAttempt attempt = reload(attemptId);
        if (attempt.getStatus() == Status.SUCCEEDED || attempt.getStatus() == Status.MISMATCH) {
            return Result.ALREADY_APPLIED;
        }
        if (!attempt.getProvider().equals(gateway.providerCode())) {
            throw ApiException.conflict("This payment belongs to a previous payment provider and cannot be refreshed");
        }
        List<GatewayPayment> payments = callGateway(() -> gateway.fetchOrderPayments(attempt.getProviderOrderId()));
        Optional<GatewayPayment> best = payments.stream().max(Comparator.comparingInt(OnlinePaymentService::rank));
        if (best.isEmpty()) {
            processor.touch(attemptId);
            return Result.IGNORED;
        }
        return processor.apply(attempt.getProvider(), best.get());
    }

    private static int rank(GatewayPayment p) {
        return p.state() == State.SUCCEEDED ? 3 : p.state() == State.PENDING ? 2 : 1;
    }

    // ------------------------------------------------------------------ webhooks

    /** Authenticates and applies one provider webhook. Redeliveries are acknowledged without being re-applied. */
    public Result handleWebhook(String provider, byte[] rawBody, Map<String, String> headers) {
        if (!provider.equalsIgnoreCase(gateway.providerCode())) {
            throw ApiException.notFound("Webhook endpoint");
        }
        WebhookEvent event;
        try {
            event = gateway.parseWebhook(rawBody, headers);
        } catch (GatewayException e) {
            if (e.getKind() == GatewayException.Kind.INVALID_SIGNATURE) {
                log.warn("Rejected {} webhook with invalid signature", provider);
                throw ApiException.unauthorized("Invalid webhook signature");
            }
            throw translate(e);
        }
        return processor.processWebhook(gateway.providerCode(), event);
    }

    // ------------------------------------------------------------------ access to attempts

    public PaymentAttempt attemptForStaff(Long attemptId) {
        PaymentAttempt a = attempts.findById(attemptId).orElseThrow(() -> ApiException.notFound("Payment attempt"));
        return authorizeStaff(a);
    }

    public PaymentAttempt attemptForStaffByOrder(String orderId) {
        PaymentAttempt a = attempts.findByProviderAndProviderOrderId(gateway.providerCode(), orderId)
                .orElseThrow(() -> ApiException.notFound("Payment attempt"));
        return authorizeStaff(a);
    }

    public PaymentAttempt attemptForLink(PaymentLink link, Long attemptId) {
        PaymentAttempt a = attempts.findById(attemptId).orElseThrow(() -> ApiException.notFound("Payment attempt"));
        return requireLink(link, a);
    }

    public PaymentAttempt attemptForLinkByOrder(PaymentLink link, String orderId) {
        PaymentAttempt a = attempts.findByProviderAndProviderOrderId(gateway.providerCode(), orderId)
                .orElseThrow(() -> ApiException.notFound("Payment attempt"));
        return requireLink(link, a);
    }

    public List<AttemptDto> attemptsForInvoice(Long invoiceId) {
        invoiceService.getAccessible(invoiceId);
        return attempts.findByInvoiceIdOrderByIdDesc(invoiceId).stream().map(this::view).toList();
    }

    private PaymentAttempt authorizeStaff(PaymentAttempt a) {
        AuthUser user = CurrentUser.get();
        if (!a.getOwnerId().equals(user.ownerId())) {
            throw ApiException.notFound("Payment attempt");
        }
        hostelAccess.checkEntity(a.getHostelId(), "Payment attempt");
        return a;
    }

    private static PaymentAttempt requireLink(PaymentLink link, PaymentAttempt a) {
        if (a.getPaymentLinkId() == null || !a.getPaymentLinkId().equals(link.getId())) {
            throw ApiException.notFound("Payment attempt");
        }
        return a;
    }

    // ------------------------------------------------------------------ helpers

    public AttemptDto view(PaymentAttempt a) {
        String receipt = a.getPaymentId() == null ? null
                : paymentRepository.findById(a.getPaymentId()).map(p -> p.getReceiptNumber()).orElse(null);
        return new AttemptDto(a.getId(), a.getInvoiceId(), a.getProvider(), a.getProviderOrderId(),
                a.getProviderPaymentId(), a.getAmount(), a.getCurrency(), a.getStatus().name(), a.getFailureReason(),
                a.getMethod(), a.isSimulated(), a.getPaymentId(), receipt, a.getInitiatedVia(), a.getCreatedAt(),
                a.getCompletedAt());
    }

    private PaymentAttempt reload(Long id) {
        return attempts.findById(id).orElseThrow(() -> ApiException.notFound("Payment attempt"));
    }

    static long toMinor(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    private static String receiptFor(Invoice invoice) {
        StringBuilder sb = new StringBuilder(invoice.getInvoiceNumber()).append('-');
        for (int i = 0; i < 6; i++) {
            sb.append(RECEIPT_CHARS.charAt(RANDOM.nextInt(RECEIPT_CHARS.length())));
        }
        return sb.length() > 40 ? sb.substring(sb.length() - 40) : sb.toString();
    }

    /** Informational only: the return path looks attempts up by provider order id and never trusts these values. */
    private static Map<String, String> orderNotes(Invoice invoice) {
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("invoice_id", String.valueOf(invoice.getId()));
        notes.put("invoice_number", invoice.getInvoiceNumber());
        notes.put("hostel_id", String.valueOf(invoice.getHostelId()));
        return notes;
    }

    private <T> T callGateway(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (GatewayException e) {
            throw translate(e);
        }
    }

    private static ApiException translate(GatewayException e) {
        return switch (e.getKind()) {
            case UNAVAILABLE -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_UNAVAILABLE",
                    "The payment provider is temporarily unavailable. Please try again shortly.");
            case NOT_CONFIGURED -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_NOT_CONFIGURED",
                    "Online payments are not fully configured.");
            case REJECTED -> new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_REJECTED",
                    "The payment provider rejected the request.");
            case INVALID_SIGNATURE -> ApiException.badRequest("Payment verification failed");
        };
    }
}
