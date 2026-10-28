package com.hostelmanagement.payments.gateway;

import java.util.List;
import java.util.Map;

/**
 * The only thing the payment business logic knows about a payment provider. Everything provider specific
 * (REST calls, signature schemes, webhook payloads, checkout parameters) lives in an adapter implementing this
 * interface; see docs/PAYMENTS.md for how to add one.
 *
 * <p>Contract for implementations:
 * <ul>
 *   <li>Amounts are integer minor units (paise) so no provider ever sees a floating point amount.</li>
 *   <li>{@link #verifyCheckout} and {@link #parseWebhook} must authenticate their input (signature) and must
 *       never report SUCCEEDED for a payment the provider has not confirmed as captured.</li>
 *   <li>Failures are reported with {@link GatewayException}; nothing here touches the database.</li>
 * </ul>
 */
public interface PaymentGatewayService {

    /** Stable lower-case identifier stored with every attempt and used in the webhook URL. */
    String providerCode();

    /** True for providers that do not move real money (shown prominently in the UI). */
    boolean isSimulated();

    /** SIMULATED, TEST (provider sandbox, no real money) or LIVE; the UI labels anything but LIVE prominently. */
    default String mode() {
        return isSimulated() ? "SIMULATED" : "LIVE";
    }

    Capabilities capabilities();

    /** Creates the provider-side order for one checkout attempt. */
    CreatedOrder createOrder(OrderRequest request);

    /**
     * Verifies what the browser handed back after checkout. Implementations check the provider signature and
     * then confirm the payment with the provider server-to-server, so a forged "success" callback is worthless.
     */
    GatewayPayment verifyCheckout(Map<String, String> checkoutFields, String expectedOrderId);

    /** Authenticates and parses a webhook delivery. {@code headers} keys are lower-case. */
    WebhookEvent parseWebhook(byte[] rawBody, Map<String, String> headers);

    /** All payments the provider holds for an order (used to resolve pending attempts without a webhook). */
    List<GatewayPayment> fetchOrderPayments(String providerOrderId);

    record Capabilities(boolean partialPayments, boolean webhooks, boolean statusLookup, List<String> paymentMethods) {
    }

    record OrderRequest(String receipt, long amountMinor, String currency, String description,
                        Map<String, String> notes) {
    }

    /** {@code checkout} holds public parameters for the browser-side checkout; it never contains secrets. */
    record CreatedOrder(String providerOrderId, long amountMinor, String currency, Map<String, Object> checkout) {
    }

    enum State { SUCCEEDED, FAILED, PENDING }

    record GatewayPayment(String providerOrderId, String providerPaymentId, State state, long amountMinor,
                          String currency, String method, String failureReason) {
    }

    /** {@code payment} is null for event types that carry no payment (they are acknowledged and ignored). */
    record WebhookEvent(String eventId, String type, GatewayPayment payment) {
    }
}
