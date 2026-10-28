package com.hostelmanagement.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "resident_id", nullable = false)
    private Long residentId;

    @Column(name = "deposit_id")
    private Long depositId;

    /** DEPOSIT or ADVANCE. */
    @Column(name = "refund_type", nullable = false, length = 20)
    private String refundType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "refund_date", nullable = false)
    private LocalDate refundDate;

    @Column(nullable = false, length = 20)
    private String method;

    @Column(name = "transaction_reference", length = 100)
    private String transactionReference;

    @Column(length = 500)
    private String reason;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
