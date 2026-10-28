package com.hostelmanagement.payments.online;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Idempotency ledger for webhook deliveries. The unique (provider, event_id) key makes "have I processed this
 * event?" an atomic database decision, so concurrent redeliveries cannot both be applied.
 */
@Repository
public class PaymentWebhookEventRepository {

    private final JdbcTemplate jdbc;

    public PaymentWebhookEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return true if this is the first time the event is seen; false for a redelivery. */
    public boolean register(String provider, String eventId, String eventType, Instant now) {
        return jdbc.update("""
                INSERT IGNORE INTO payment_webhook_events (provider, event_id, event_type, received_at)
                VALUES (?, ?, ?, ?)""", provider, eventId, eventType, Timestamp.from(now)) == 1;
    }
}
