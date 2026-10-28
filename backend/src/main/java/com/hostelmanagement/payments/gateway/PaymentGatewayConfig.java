package com.hostelmanagement.payments.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostelmanagement.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.client.RestClient;

/**
 * The single place where {@code app.payment.provider} is turned into an adapter. Everything else depends on
 * {@link PaymentGatewayService} only. Misconfiguration fails start-up with a precise message; the application never
 * silently falls back to the mock when a real provider was requested.
 *
 * <p>To add a provider, implement {@link PaymentGatewayService}, add a {@code case} below, and add its settings to
 * {@code AppProperties.Payment}.
 */
@Slf4j
@Configuration
public class PaymentGatewayConfig {

    @Bean
    public PaymentGatewayService paymentGatewayService(AppProperties props, Environment env, ObjectMapper mapper,
                                                       RestClient.Builder restClientBuilder) {
        AppProperties.Payment cfg = props.payment();
        String provider = cfg.provider() == null ? "" : cfg.provider().trim().toLowerCase();
        boolean prod = env.acceptsProfiles(Profiles.of("prod"));
        return switch (provider) {
            case MockPaymentGatewayService.CODE -> mock(prod, mapper);
            case RazorpayPaymentGatewayService.CODE -> razorpay(cfg.razorpay(), prod, mapper, restClientBuilder);
            default -> throw new IllegalStateException("Unsupported app.payment.provider '" + cfg.provider()
                    + "'. Supported providers: mock, razorpay. See docs/PAYMENTS.md to add another.");
        };
    }

    private static PaymentGatewayService mock(boolean prod, ObjectMapper mapper) {
        if (prod) {
            throw new IllegalStateException("app.payment.provider=mock is not allowed with the 'prod' profile. "
                    + "Set PAYMENT_PROVIDER to a real provider.");
        }
        log.warn("Payments are running in MOCK mode: checkout is simulated and NO real money is collected");
        return new MockPaymentGatewayService(mapper);
    }

    private static PaymentGatewayService razorpay(AppProperties.Payment.Razorpay r, boolean prod, ObjectMapper mapper,
                                                  RestClient.Builder builder) {
        if (blank(r.keyId()) || blank(r.keySecret())) {
            throw new IllegalStateException("PAYMENT_PROVIDER=razorpay requires RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET "
                    + "(Razorpay Dashboard > Account & Settings > API Keys, in Test Mode). Put them in backend/.env.");
        }
        boolean liveKey = r.keyId().startsWith("rzp_live_");
        if (!r.keyId().startsWith("rzp_test_") && !liveKey) {
            throw new IllegalStateException("RAZORPAY_KEY_ID does not look like a Razorpay key (expected rzp_test_... "
                    + "or rzp_live_...)");
        }
        if (liveKey && !prod) {
            throw new IllegalStateException("A live Razorpay key (rzp_live_...) is configured outside the 'prod' "
                    + "profile. Use Test Mode keys (rzp_test_...) for development.");
        }
        if (blank(r.webhookSecret())) {
            log.warn("RAZORPAY_WEBHOOK_SECRET is not set: webhooks will be rejected (HTTP 503) until it is "
                    + "configured. Checkout verification and status refresh still work.");
        }
        log.info("Payments are using Razorpay in {} mode", liveKey ? "LIVE" : "TEST");
        return new RazorpayPaymentGatewayService(r, RazorpayPaymentGatewayService.buildClient(r, builder), mapper);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
