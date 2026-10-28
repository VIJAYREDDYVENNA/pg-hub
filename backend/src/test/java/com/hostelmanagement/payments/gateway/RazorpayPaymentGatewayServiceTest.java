package com.hostelmanagement.payments.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostelmanagement.config.AppProperties;
import com.hostelmanagement.payments.gateway.GatewayException.Kind;
import com.hostelmanagement.payments.gateway.PaymentGatewayService.CreatedOrder;
import com.hostelmanagement.payments.gateway.PaymentGatewayService.GatewayPayment;
import com.hostelmanagement.payments.gateway.PaymentGatewayService.OrderRequest;
import com.hostelmanagement.payments.gateway.PaymentGatewayService.State;
import com.hostelmanagement.payments.gateway.PaymentGatewayService.WebhookEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Razorpay adapter against a mocked HTTP server: no network, no credentials, real signature maths. */
class RazorpayPaymentGatewayServiceTest {

    private static final String KEY_ID = "rzp_test_unitTestKey";
    private static final String KEY_SECRET = "unit-test-key-secret";
    private static final String WEBHOOK_SECRET = "unit-test-webhook-secret";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockRestServiceServer server;
    private RazorpayPaymentGatewayService gateway;

    @BeforeEach
    void setUp() {
        gateway = build(WEBHOOK_SECRET);
    }

    private RazorpayPaymentGatewayService build(String webhookSecret) {
        var cfg = new AppProperties.Payment.Razorpay(KEY_ID, KEY_SECRET, webhookSecret, "https://api.razorpay.test/v1");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.baseUrl(cfg.baseUrl()).build();
        return new RazorpayPaymentGatewayService(cfg, client, MAPPER);
    }

    private static String sign(String secret, String data) {
        return HmacSignatures.hmacSha256Hex(secret, data);
    }

    private static String paymentJson(String id, String orderId, String status, long amount, String extra) {
        return "{\"id\":\"%s\",\"order_id\":\"%s\",\"status\":\"%s\",\"amount\":%d,\"currency\":\"INR\",\"method\":\"upi\"%s}"
                .formatted(id, orderId, status, amount, extra);
    }

    // ------------------------------------------------------------------ orders

    @Test
    void createOrder_sendsMinorUnits_andReturnsOnlyPublicCheckoutData() {
        server.expect(requestTo("https://api.razorpay.test/v1/orders")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.amount").value(150050))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.receipt").value("INV-1-ABC"))
                .andExpect(jsonPath("$.notes.invoice_id").value("42"))
                .andRespond(withSuccess("{\"id\":\"order_abc\",\"amount\":150050,\"currency\":\"INR\"}", MediaType.APPLICATION_JSON));

        CreatedOrder order = gateway.createOrder(new OrderRequest("INV-1-ABC", 150050, "INR", "Invoice INV-1",
                Map.of("invoice_id", "42")));

        assertEquals("order_abc", order.providerOrderId());
        assertEquals(KEY_ID, order.checkout().get("key"));
        assertEquals("order_abc", order.checkout().get("orderId"));
        assertTrue(order.checkout().values().stream().noneMatch(v -> String.valueOf(v).contains(KEY_SECRET)),
                "the key secret must never reach the browser");
        server.verify();
    }

    @Test
    void createOrder_authenticatesWithBasicCredentials() {
        var cfg = new AppProperties.Payment.Razorpay(KEY_ID, KEY_SECRET, WEBHOOK_SECRET, "https://api.razorpay.test/v1");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer s = MockRestServiceServer.bindTo(builder).build();
        // Same construction path as production (credentials are attached by buildClient)
        var real = new RazorpayPaymentGatewayService(cfg, RazorpayPaymentGatewayService.authenticated(builder, cfg), MAPPER);
        String expected = "Basic " + java.util.Base64.getEncoder().encodeToString((KEY_ID + ":" + KEY_SECRET).getBytes(StandardCharsets.UTF_8));
        s.expect(requestTo("https://api.razorpay.test/v1/orders")).andExpect(header("Authorization", expected))
                .andRespond(withSuccess("{\"id\":\"order_1\",\"amount\":100,\"currency\":\"INR\"}", MediaType.APPLICATION_JSON));

        real.createOrder(new OrderRequest("r", 100, "INR", "d", Map.of()));

        s.verify();
    }

    @Test
    void createOrder_mapsProviderErrors() {
        server.expect(requestTo("https://api.razorpay.test/v1/orders")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"description\":\"bad\"}}"));
        assertEquals(Kind.REJECTED, assertThrows(GatewayException.class,
                () -> gateway.createOrder(new OrderRequest("r", 100, "INR", "d", Map.of()))).getKind());

        server.reset();
        server.expect(requestTo("https://api.razorpay.test/v1/orders")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertEquals(Kind.UNAVAILABLE, assertThrows(GatewayException.class,
                () -> gateway.createOrder(new OrderRequest("r", 100, "INR", "d", Map.of()))).getKind());

        server.reset();
        server.expect(requestTo("https://api.razorpay.test/v1/orders")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertEquals(Kind.UNAVAILABLE, assertThrows(GatewayException.class,
                () -> gateway.createOrder(new OrderRequest("r", 100, "INR", "d", Map.of()))).getKind());
    }

    // ------------------------------------------------------------------ checkout verification

    @Test
    void verifyCheckout_validSignature_andCapturedPayment_succeeds() {
        server.expect(requestTo("https://api.razorpay.test/v1/payments/pay_1")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(paymentJson("pay_1", "order_1", "captured", 50000, ""), MediaType.APPLICATION_JSON));

        GatewayPayment p = gateway.verifyCheckout(Map.of("razorpay_order_id", "order_1", "razorpay_payment_id", "pay_1",
                "razorpay_signature", sign(KEY_SECRET, "order_1|pay_1")), "order_1");

        assertEquals(State.SUCCEEDED, p.state());
        assertEquals(50000, p.amountMinor());
        assertEquals("INR", p.currency());
        assertEquals("upi", p.method());
        server.verify();
    }

    @Test
    void verifyCheckout_badSignature_isRejected_withoutCallingTheProvider() {
        // No expectation registered: any HTTP call would fail the test
        GatewayException e = assertThrows(GatewayException.class, () -> gateway.verifyCheckout(
                Map.of("razorpay_order_id", "order_1", "razorpay_payment_id", "pay_1", "razorpay_signature", "forged"), "order_1"));

        assertEquals(Kind.INVALID_SIGNATURE, e.getKind());
        server.verify();
    }

    @Test
    void verifyCheckout_signatureForADifferentOrder_isRejected() {
        String sigForOther = sign(KEY_SECRET, "order_other|pay_1");

        assertEquals(Kind.INVALID_SIGNATURE, assertThrows(GatewayException.class, () -> gateway.verifyCheckout(
                Map.of("razorpay_order_id", "order_other", "razorpay_payment_id", "pay_1", "razorpay_signature", sigForOther),
                "order_1")).getKind());
        // Right order id claimed, but signature computed for another order
        assertEquals(Kind.INVALID_SIGNATURE, assertThrows(GatewayException.class, () -> gateway.verifyCheckout(
                Map.of("razorpay_order_id", "order_1", "razorpay_payment_id", "pay_1", "razorpay_signature", sigForOther),
                "order_1")).getKind());
    }

    @Test
    void verifyCheckout_missingFields_isRejected() {
        assertEquals(Kind.INVALID_SIGNATURE, assertThrows(GatewayException.class,
                () -> gateway.verifyCheckout(Map.of("razorpay_order_id", "order_1"), "order_1")).getKind());
    }

    @Test
    void verifyCheckout_validSignatureButNotCaptured_isNotSuccess() {
        server.expect(requestTo("https://api.razorpay.test/v1/payments/pay_1"))
                .andRespond(withSuccess(paymentJson("pay_1", "order_1", "authorized", 50000, ""), MediaType.APPLICATION_JSON));

        GatewayPayment p = gateway.verifyCheckout(Map.of("razorpay_order_id", "order_1", "razorpay_payment_id", "pay_1",
                "razorpay_signature", sign(KEY_SECRET, "order_1|pay_1")), "order_1");

        assertEquals(State.PENDING, p.state());
    }

    @Test
    void verifyCheckout_paymentBelongingToAnotherOrder_isRejected() {
        server.expect(requestTo("https://api.razorpay.test/v1/payments/pay_1"))
                .andRespond(withSuccess(paymentJson("pay_1", "order_999", "captured", 50000, ""), MediaType.APPLICATION_JSON));

        assertEquals(Kind.INVALID_SIGNATURE, assertThrows(GatewayException.class, () -> gateway.verifyCheckout(
                Map.of("razorpay_order_id", "order_1", "razorpay_payment_id", "pay_1",
                        "razorpay_signature", sign(KEY_SECRET, "order_1|pay_1")), "order_1")).getKind());
    }

    @Test
    void verifyCheckout_providerDown_isReportedAsUnavailable() {
        server.expect(requestTo("https://api.razorpay.test/v1/payments/pay_1")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertEquals(Kind.UNAVAILABLE, assertThrows(GatewayException.class, () -> gateway.verifyCheckout(
                Map.of("razorpay_order_id", "order_1", "razorpay_payment_id", "pay_1",
                        "razorpay_signature", sign(KEY_SECRET, "order_1|pay_1")), "order_1")).getKind());
    }

    // ------------------------------------------------------------------ webhooks

    private static final String CAPTURED_BODY = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
            + "{\"id\":\"pay_9\",\"order_id\":\"order_9\",\"status\":\"captured\",\"amount\":12300,\"currency\":\"INR\",\"method\":\"card\"}}}}";

    @Test
    void webhook_withValidSignature_isParsed() {
        byte[] raw = CAPTURED_BODY.getBytes(StandardCharsets.UTF_8);

        WebhookEvent e = gateway.parseWebhook(raw, Map.of("x-razorpay-signature", sign(WEBHOOK_SECRET, CAPTURED_BODY),
                "x-razorpay-event-id", "evt_123"));

        assertEquals("evt_123", e.eventId());
        assertEquals("payment.captured", e.type());
        assertEquals(State.SUCCEEDED, e.payment().state());
        assertEquals("order_9", e.payment().providerOrderId());
        assertEquals(12300, e.payment().amountMinor());
    }

    @Test
    void webhook_withoutEventIdHeader_usesAStableBodyHash() {
        byte[] raw = CAPTURED_BODY.getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = Map.of("x-razorpay-signature", sign(WEBHOOK_SECRET, CAPTURED_BODY));

        String first = gateway.parseWebhook(raw, headers).eventId();
        String second = gateway.parseWebhook(raw, headers).eventId();

        assertEquals(first, second);
        assertTrue(first.startsWith("sha256:"));
    }

    @Test
    void webhook_isVerifiedOverTheExactRawBytes() {
        String signed = sign(WEBHOOK_SECRET, CAPTURED_BODY);
        // Same JSON, different whitespace: a re-serialised body must not verify
        byte[] reformatted = CAPTURED_BODY.replace(",", ", ").getBytes(StandardCharsets.UTF_8);

        assertEquals(Kind.INVALID_SIGNATURE, assertThrows(GatewayException.class,
                () -> gateway.parseWebhook(reformatted, Map.of("x-razorpay-signature", signed))).getKind());
    }

    @Test
    void webhook_withWrongOrMissingSignature_isRejected() {
        byte[] raw = CAPTURED_BODY.getBytes(StandardCharsets.UTF_8);

        assertEquals(Kind.INVALID_SIGNATURE, assertThrows(GatewayException.class,
                () -> gateway.parseWebhook(raw, Map.of("x-razorpay-signature", sign("another-secret", CAPTURED_BODY)))).getKind());
        assertEquals(Kind.INVALID_SIGNATURE, assertThrows(GatewayException.class,
                () -> gateway.parseWebhook(raw, Map.of())).getKind());
    }

    @Test
    void webhook_withoutConfiguredSecret_isNotConfigured_notAccepted() {
        RazorpayPaymentGatewayService noSecret = build("");
        byte[] raw = CAPTURED_BODY.getBytes(StandardCharsets.UTF_8);

        assertEquals(Kind.NOT_CONFIGURED, assertThrows(GatewayException.class,
                () -> noSecret.parseWebhook(raw, Map.of("x-razorpay-signature", "anything"))).getKind());
    }

    @Test
    void webhook_failedAndUnrelatedEvents() {
        String failed = "{\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_f\",\"order_id\":\"order_f\","
                + "\"status\":\"failed\",\"amount\":500,\"currency\":\"INR\",\"error_description\":\"Insufficient funds\"}}}}";
        WebhookEvent f = gateway.parseWebhook(failed.getBytes(StandardCharsets.UTF_8),
                Map.of("x-razorpay-signature", sign(WEBHOOK_SECRET, failed)));
        assertEquals(State.FAILED, f.payment().state());
        assertEquals("Insufficient funds", f.payment().failureReason());

        String refund = "{\"event\":\"refund.processed\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"rfnd_1\"}}}}";
        WebhookEvent r = gateway.parseWebhook(refund.getBytes(StandardCharsets.UTF_8),
                Map.of("x-razorpay-signature", sign(WEBHOOK_SECRET, refund)));
        assertNull(r.payment(), "events without a payment are acknowledged and ignored");
    }

    // ------------------------------------------------------------------ reconciliation

    @Test
    void fetchOrderPayments_mapsEveryPaymentOfTheOrder() {
        String items = "{\"items\":[" + paymentJson("pay_a", "order_1", "failed", 500, ",\"error_description\":\"declined\"") + ","
                + paymentJson("pay_b", "order_1", "captured", 500, "") + "]}";
        server.expect(requestTo("https://api.razorpay.test/v1/orders/order_1/payments"))
                .andRespond(withSuccess(items, MediaType.APPLICATION_JSON));

        List<GatewayPayment> payments = gateway.fetchOrderPayments("order_1");

        assertEquals(2, payments.size());
        assertEquals(State.FAILED, payments.get(0).state());
        assertEquals(State.SUCCEEDED, payments.get(1).state());
    }

    @Test
    void refundedPayment_isNeverTreatedAsMoneyReceived() throws Exception {
        GatewayPayment p = RazorpayPaymentGatewayService.toPayment(
                MAPPER.readTree(paymentJson("pay_r", "order_1", "refunded", 500, "")));

        assertEquals(State.FAILED, p.state());
    }
}
