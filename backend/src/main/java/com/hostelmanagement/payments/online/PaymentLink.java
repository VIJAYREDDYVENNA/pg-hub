package com.hostelmanagement.payments.online;

import com.hostelmanagement.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A capability link a resident can open without an account to pay one invoice. Only the token's hash is stored. */
@Getter
@Setter
@Entity
@Table(name = "payment_links")
public class PaymentLink extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    public boolean isUsable(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
