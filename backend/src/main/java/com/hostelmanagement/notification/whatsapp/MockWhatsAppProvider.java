package com.hostelmanagement.notification.whatsapp;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Development provider: records the message as "sent" without contacting WhatsApp. Messages are stored with
 * simulated=true and the UI labels them as simulated. Phone numbers ending in 0000 simulate a temporary failure
 * so retry handling can be exercised locally.
 */
@Slf4j
public class MockWhatsAppProvider implements WhatsAppProvider {

    @Override
    public boolean isSimulated() {
        return true;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public SendResult send(OutboundMessage message) {
        if (message.toPhone().endsWith("0000")) {
            return SendResult.failed("Simulated temporary failure (numbers ending in 0000)", true);
        }
        log.info("[SIMULATED WhatsApp - not sent] to {}: {}", message.toPhone(), message.body());
        return SendResult.ok("mock-" + UUID.randomUUID());
    }
}
