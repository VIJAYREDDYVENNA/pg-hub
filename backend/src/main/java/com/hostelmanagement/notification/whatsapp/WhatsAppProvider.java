package com.hostelmanagement.notification.whatsapp;

import java.util.List;

/** Sends a single WhatsApp message through a concrete provider. */
public interface WhatsAppProvider {

    record OutboundMessage(String toPhone, String body, String providerTemplate, String languageCode,
                           List<String> templateParameters) {
    }

    /**
     * @param transientFailure true when the error is temporary (network, rate limit, 5xx) and may be retried.
     */
    record SendResult(boolean success, String providerMessageId, String error, boolean transientFailure) {
        static SendResult ok(String id) {
            return new SendResult(true, id, null, false);
        }

        static SendResult failed(String error, boolean transientFailure) {
            return new SendResult(false, null, error, transientFailure);
        }
    }

    boolean isSimulated();

    boolean isConfigured();

    SendResult send(OutboundMessage message);
}
