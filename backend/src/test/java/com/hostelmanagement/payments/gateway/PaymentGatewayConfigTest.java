package com.hostelmanagement.payments.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostelmanagement.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Provider selection is configuration-driven and misconfiguration fails loudly instead of falling back. */
class PaymentGatewayConfigTest {

    private final PaymentGatewayConfig config = new PaymentGatewayConfig();

    private PaymentGatewayService select(String provider, String keyId, String secret, String webhook, String... profiles) {
        var razorpay = new AppProperties.Payment.Razorpay(keyId, secret, webhook, "https://api.razorpay.com/v1");
        var payment = new AppProperties.Payment(provider, "INR", new BigDecimal("100"), 7, razorpay);
        AppProperties props = new AppProperties(null, null, null, null, null, null, null, null, payment, null, null, null);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return config.paymentGatewayService(props, env, new ObjectMapper(), RestClient.builder());
    }

    @Test
    void mock_isSelected_andReportsItselfAsSimulated() {
        PaymentGatewayService gw = select("mock", null, null, null, "dev");

        assertInstanceOf(MockPaymentGatewayService.class, gw);
        assertTrue(gw.isSimulated());
        assertEquals("mock", gw.providerCode());
    }

    @Test
    void provider_isCaseAndWhitespaceInsensitive() {
        assertInstanceOf(MockPaymentGatewayService.class, select("  MOCK ", null, null, null, "dev"));
    }

    @Test
    void razorpay_isSelected_withTestKeys() {
        PaymentGatewayService gw = select("razorpay", "rzp_test_abc", "secret", "whsec", "dev");

        assertInstanceOf(RazorpayPaymentGatewayService.class, gw);
        assertFalse(gw.isSimulated());
        assertEquals("razorpay", gw.providerCode());
    }

    @Test
    void razorpay_withoutCredentials_failsStartup_insteadOfFallingBackToMock() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> select("razorpay", "", "", "", "dev"));
        assertTrue(e.getMessage().contains("RAZORPAY_KEY_ID"), e.getMessage());

        assertThrows(IllegalStateException.class, () -> select("razorpay", "rzp_test_abc", null, "w", "dev"));
    }

    @Test
    void razorpay_rejectsMalformedKeyIds() {
        assertThrows(IllegalStateException.class, () -> select("razorpay", "not-a-key", "secret", "w", "dev"));
    }

    @Test
    void liveKeys_areRefusedOutsideProd() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> select("razorpay", "rzp_live_abc", "secret", "w", "dev"));
        assertTrue(e.getMessage().contains("Test Mode"), e.getMessage());

        assertInstanceOf(RazorpayPaymentGatewayService.class, select("razorpay", "rzp_live_abc", "secret", "w", "prod"));
    }

    @Test
    void razorpay_withoutWebhookSecret_starts_butWebhooksAreRejected() {
        PaymentGatewayService gw = select("razorpay", "rzp_test_abc", "secret", "", "dev");

        assertEquals(GatewayException.Kind.NOT_CONFIGURED, assertThrows(GatewayException.class,
                () -> gw.parseWebhook("{}".getBytes(), java.util.Map.of("x-razorpay-signature", "x"))).getKind());
    }

    @Test
    void mock_isForbiddenInProd() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> select("mock", null, null, null, "prod"));
        assertTrue(e.getMessage().contains("prod"), e.getMessage());
    }

    @Test
    void unknownProvider_failsWithTheSupportedList() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> select("phonepe", null, null, null, "dev"));
        assertTrue(e.getMessage().contains("mock, razorpay"), e.getMessage());
    }
}
