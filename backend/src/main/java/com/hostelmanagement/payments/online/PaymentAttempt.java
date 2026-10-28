package com.hostelmanagement.payments.online;

import com.hostelmanagement.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One gateway checkout for an invoice. This is the gateway-side record; money only reaches the invoice through the
 * {@code Payment} created when the attempt reaches SUCCEEDED.
 */
@Getter
@Setter
@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt extends BaseEntity {

    /**
     * CREATED: order exists, nothing submitted. PENDING: submitted, outcome not final. FAILED: the latest try failed
     * (the order may still be paid later, so this is not terminal). SUCCEEDED: verified and applied (terminal).
     * EXPIRED: abandoned. MISMATCH: provider reports a capture that does not match this attempt; not applied,
     * needs manual review (terminal).
     */
    public enum Status { CREATED, PENDING, FAILED, SUCCEEDED, EXPIRED, MISMATCH }

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "resident_id", nullable = false)
    private Long residentId;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_order_id", nullable = false, length = 100)
    private String providerOrderId;

    @Column(name = "provider_payment_id", length = 100)
    private String providerPaymentId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.CREATED;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(length = 40)
    private String method;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "initiated_by", nullable = false)
    private Long initiatedBy;

    /** STAFF or LINK. */
    @Column(name = "initiated_via", nullable = false, length = 10)
    private String initiatedVia;

    @Column(name = "payment_link_id")
    private Long paymentLinkId;

    @Column(nullable = false)
    private boolean simulated;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Version
    private Long version;

    public boolean isOpen() {
        return status == Status.CREATED || status == Status.PENDING;
    }
}
