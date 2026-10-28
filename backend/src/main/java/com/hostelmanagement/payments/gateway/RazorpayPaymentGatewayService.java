package com.hostelmanagement.payments.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hostelmanagement.config.AppProperties;
import com.hostelmanagement.payments.gateway.GatewayException.Kind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Razorpay adapter (Orders API + Standard Checkout), implemented against the documented REST API and signature
 * schemes:
 * <ul>
 *   <li>Checkout signature = HMAC-SHA256(key_secret, order_id + "|" + payment_id).</li>
 *   <li>Webhook signature = HMAC-SHA256(webhook_secret, raw request body) in {@code X-Razorpay-Signature}.</li>
 * </ul>
 * A valid checkout signature only proves the payment belongs to the order, so {@link #verifyCheckout} also fetches
 * the payment from Razorpay and reports SUCCEEDED only when its status is {@code captured}.
 *
 * <p>Secrets stay on the server: the browser receives only the public key id and the order id.
 */
@Slf4j
public class RazorpayPaymentGatewayService implements PaymentGatewayService {

    public static final String CODE = "razorpay";

    private final AppProperties.Payment.Razorpay cfg;
    private final RestClient http;
    private final ObjectMapper mapper;

    /** {@code http} must already carry the base URL and Basic credentials (see PaymentGatewayConfig). */
    public RazorpayPaymentGatewayService(AppProperties.Payment.Razorpay cfg, RestClient http, ObjectMapper mapper) {
        this.cfg = cfg;
        this.http = http;
        this.mapper = mapper;
    }

    @Override
    public String providerCode() {
        return CODE;
    }

    @Override
    public boolean isSimulated() {
        return false;
    }

    @Override
    public String mode() {
        return cfg.keyId() != null && cfg.keyId().startsWith("rzp_test_") ? "TEST" : "LIVE";
    }

    @Override
    public Capabilities capabilities() {
        // Methods are enabled per merchant in the Razorpay dashboard and shown by Checkout itself.
        return new Capabilities(true, true, true, List.of("UPI", "Cards", "Netbanking", "Wallets"));
    }

    @Override
    public CreatedOrder createOrder(OrderRequest req) {
        ObjectNode body = mapper.createObjectNode();
        body.put("amount", req.amountMinor());
        body.put("currency", req.currency());
        body.put("receipt", req.receipt());
        ObjectNode notes = body.putObject("notes");
        req.notes().forEach(notes::put);
        JsonNode order = call(() -> http.post().uri("/orders").contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(JsonNode.class));
        String orderId = text(order, "id");
        if (orderId == null) {
            throw new GatewayException(Kind.REJECTED, "Razorpay did not return an order id");
        }
        Map<String, Object> checkout = new LinkedHashMap<>();
        checkout.put("provider", CODE);
        checkout.put("key", cfg.keyId());
        checkout.put("orderId", orderId);
        checkout.put("amount", order.path("amount").asLong(req.amountMinor()));
        checkout.put("currency", text(order, "currency"));
        checkout.put("description", req.description());
        return new CreatedOrder(orderId, order.path("amount").asLong(req.amountMinor()), text(order, "currency"), checkout);
    }

    @Override
    public GatewayPayment verifyCheckout(Map<String, String> fields, String expectedOrderId) {
        String orderId = fields.get("razorpay_order_id");
        String paymentId = fields.get("razorpay_payment_id");
        String signature = fields.get("razorpay_signature");
        if (orderId == null || paymentId == null || signature == null || !orderId.equals(expectedOrderId)) {
            throw new GatewayException(Kind.INVALID_SIGNATURE, "Checkout response does not match the order");
        }
        String expected = HmacSignatures.hmacSha256Hex(cfg.keySecret(), orderId + "|" + paymentId);
        if (!HmacSignatures.matches(expected, signature)) {
            throw new GatewayException(Kind.INVALID_SIGNATURE, "Checkout signature mismatch");
        }
        JsonNode payment = call(() -> http.get().uri("/payments/{id}", paymentId).retrieve().body(JsonNode.class));
        GatewayPayment result = toPayment(payment);
        if (!expectedOrderId.equals(result.providerOrderId()) || !paymentId.equals(result.providerPaymentId())) {
            throw new GatewayException(Kind.INVALID_SIGNATURE, "Payment does not belong to the order");
        }
        return result;
    }

    @Override
    public WebhookEvent parseWebhook(byte[] rawBody, Map<String, String> headers) {
        if (cfg.webhookSecret() == null || cfg.webhookSecret().isBlank()) {
            throw new GatewayException(Kind.NOT_CONFIGURED, "RAZORPAY_WEBHOOK_SECRET is not configured");
        }
        // The signature covers the exact bytes Razorpay sent, so it is computed before any JSON parsing
        String expected = HmacSignatures.hmacSha256Hex(cfg.webhookSecret(), rawBody);
        if (!HmacSignatures.matches(expected, headers.get("x-razorpay-signature"))) {
            throw new GatewayException(Kind.INVALID_SIGNATURE, "Webhook signature mismatch");
        }
        try {
            JsonNode root = mapper.readTree(rawBody);
            String type = root.path("event").asText("");
            String eventId = headers.get("x-razorpay-event-id");
            if (eventId == null || eventId.isBlank()) {
                eventId = "sha256:" + sha256Hex(rawBody);
            }
            JsonNode entity = root.path("payload").path("payment").path("entity");
            boolean paymentEvent = type.startsWith("payment.") || "order.paid".equals(type);
            return new WebhookEvent(eventId, type, paymentEvent && !entity.isMissingNode() ? toPayment(entity) : null);
        } catch (IOException e) {
            throw new GatewayException(Kind.REJECTED, "Malformed webhook payload", e);
        }
    }

    @Override
    public List<GatewayPayment> fetchOrderPayments(String providerOrderId) {
        JsonNode list = call(() -> http.get().uri("/orders/{id}/payments", providerOrderId).retrieve().body(JsonNode.class));
        List<GatewayPayment> result = new ArrayList<>();
        for (JsonNode item : list.path("items")) {
            result.add(toPayment(item));
        }
        return result;
    }

    /** Maps a Razorpay payment entity. Only {@code captured} counts as money received. */
    static GatewayPayment toPayment(JsonNode p) {
        String status = p.path("status").asText("");
        State state = switch (status) {
            case "captured" -> State.SUCCEEDED;
            case "failed", "refunded" -> State.FAILED;
            default -> State.PENDING; // created, authorized (not yet captured)
        };
        String reason = null;
        if (state == State.FAILED) {
            reason = "refunded".equals(status) ? "Payment was refunded" : text(p, "error_description");
        }
        return new GatewayPayment(text(p, "order_id"), text(p, "id"), state, p.path("amount").asLong(-1),
                text(p, "currency"), text(p, "method"), reason);
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        try {
            T result = request.get();
            if (result == null) {
                throw new GatewayException(Kind.UNAVAILABLE, "Empty response from Razorpay");
            }
            return result;
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            boolean retryable = status.is5xxServerError() || status.value() == 429;
            log.warn("Razorpay API error {}: {}", status.value(), e.getResponseBodyAsString());
            throw new GatewayException(retryable ? Kind.UNAVAILABLE : Kind.REJECTED,
                    "Razorpay returned HTTP " + status.value(), e);
        } catch (ResourceAccessException e) {
            throw new GatewayException(Kind.UNAVAILABLE, "Could not reach Razorpay", e);
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Builds the HTTP client for the Razorpay API: base URL, Basic credentials and conservative timeouts. */
    public static RestClient buildClient(AppProperties.Payment.Razorpay cfg, RestClient.Builder builder) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        return authenticated(builder.requestFactory(factory), cfg);
    }

    /** Base URL and Basic credentials only; split out so tests can bind a mock server to the builder. */
    static RestClient authenticated(RestClient.Builder builder, AppProperties.Payment.Razorpay cfg) {
        String basic = java.util.Base64.getEncoder()
                .encodeToString((cfg.keyId() + ":" + cfg.keySecret()).getBytes(StandardCharsets.UTF_8));
        return builder.baseUrl(cfg.baseUrl()).defaultHeader("Authorization", "Basic " + basic).build();
    }
}
