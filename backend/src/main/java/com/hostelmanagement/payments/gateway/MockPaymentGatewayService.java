package com.hostelmanagement.payments.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostelmanagement.payments.gateway.GatewayException.Kind;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Development gateway: no network, no money. Orders and their simulated outcomes live in memory (they are lost on
 * restart, which is fine for a simulator). The browser reports the outcome the developer picked in the simulated
 * checkout; because this adapter refuses to start under the {@code prod} profile there is no way to use it to
 * mark real invoices paid in production.
 *
 * <p>Webhooks are simulated too: a body signed with {@link #WEBHOOK_SECRET} in the {@code x-mock-signature}
 * header is accepted, which lets tests and curl exercise the same webhook path as a real provider.
 */
@Slf4j
public class MockPaymentGatewayService implements PaymentGatewayService {

    public static final String CODE = "mock";
    public static final String WEBHOOK_SECRET = "mock-webhook-secret";
    public static final String SIGNATURE_HEADER = "x-mock-signature";

    private final ObjectMapper mapper;
    private final Map<String, Long> orderAmounts = new ConcurrentHashMap<>();
    private final Map<String, List<GatewayPayment>> paymentsByOrder = new ConcurrentHashMap<>();

    public MockPaymentGatewayService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String providerCode() {
        return CODE;
    }

    @Override
    public boolean isSimulated() {
        return true;
    }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(true, true, true, List.of("Simulated checkout"));
    }

    @Override
    public CreatedOrder createOrder(OrderRequest request) {
        String orderId = "order_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        orderAmounts.put(orderId, request.amountMinor());
        Map<String, Object> checkout = new LinkedHashMap<>();
        checkout.put("provider", CODE);
        checkout.put("orderId", orderId);
        checkout.put("amount", request.amountMinor());
        checkout.put("currency", request.currency());
        checkout.put("description", request.description());
        log.info("[SIMULATED payment] order {} created for {} minor units - no real money involved", orderId,
                request.amountMinor());
        return new CreatedOrder(orderId, request.amountMinor(), request.currency(), checkout);
    }

    @Override
    public GatewayPayment verifyCheckout(Map<String, String> fields, String expectedOrderId) {
        Long amount = orderAmounts.get(expectedOrderId);
        if (amount == null) {
            throw new GatewayException(Kind.REJECTED, "Unknown simulated order");
        }
        String paymentId = fields.get("mock_payment_id");
        if (paymentId == null || paymentId.isBlank()) {
            throw new GatewayException(Kind.INVALID_SIGNATURE, "Missing simulated payment id");
        }
        State state = switch (String.valueOf(fields.get("mock_outcome")).toUpperCase()) {
            case "SUCCESS" -> State.SUCCEEDED;
            case "FAILED" -> State.FAILED;
            case "PENDING" -> State.PENDING;
            default -> throw new GatewayException(Kind.INVALID_SIGNATURE, "Unknown simulated outcome");
        };
        GatewayPayment payment = new GatewayPayment(expectedOrderId, paymentId, state, amount, "INR", "simulated",
                state == State.FAILED ? "Simulated payment failure" : null);
        record(payment);
        return payment;
    }

    /** Dev helper: resolves a payment that was left PENDING, like a bank confirming late. */
    public GatewayPayment settle(String orderId, State outcome) {
        List<GatewayPayment> list = paymentsByOrder.get(orderId);
        if (list == null || list.isEmpty()) {
            throw new GatewayException(Kind.REJECTED, "No simulated payment exists for this order yet");
        }
        GatewayPayment last = list.get(list.size() - 1);
        GatewayPayment settled = new GatewayPayment(last.providerOrderId(), last.providerPaymentId(), outcome,
                last.amountMinor(), last.currency(), last.method(),
                outcome == State.FAILED ? "Simulated payment failure" : null);
        record(settled);
        return settled;
    }

    @Override
    public WebhookEvent parseWebhook(byte[] rawBody, Map<String, String> headers) {
        String signature = headers.get(SIGNATURE_HEADER);
        if (!HmacSignatures.matches(HmacSignatures.hmacSha256Hex(WEBHOOK_SECRET, rawBody), signature)) {
            throw new GatewayException(Kind.INVALID_SIGNATURE, "Webhook signature mismatch");
        }
        try {
            JsonNode n = mapper.readTree(rawBody);
            GatewayPayment payment = n.hasNonNull("paymentId")
                    ? new GatewayPayment(n.path("orderId").asText(), n.path("paymentId").asText(),
                    State.valueOf(n.path("state").asText("PENDING")), n.path("amountMinor").asLong(),
                    n.path("currency").asText("INR"), "simulated", n.path("failureReason").asText(null))
                    : null;
            if (payment != null) {
                // Webhook outcomes are what a real provider would also report on a later fetch
                record(payment);
            }
            return new WebhookEvent(n.path("id").asText(), n.path("type").asText(), payment);
        } catch (IOException | IllegalArgumentException e) {
            throw new GatewayException(Kind.REJECTED, "Malformed webhook payload", e);
        }
    }

    @Override
    public List<GatewayPayment> fetchOrderPayments(String providerOrderId) {
        return new ArrayList<>(paymentsByOrder.getOrDefault(providerOrderId, List.of()));
    }

    private void record(GatewayPayment payment) {
        paymentsByOrder.compute(payment.providerOrderId(), (k, list) -> {
            List<GatewayPayment> next = list == null ? new ArrayList<>() : new ArrayList<>(list);
            next.removeIf(p -> p.providerPaymentId().equals(payment.providerPaymentId()));
            next.add(payment);
            return next;
        });
    }
}
