package com.hostelmanagement.payments;

import com.hostelmanagement.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "resident_id", nullable = false)
    private Long residentId;

    @Column(name = "admission_id")
    private Long admissionId;

    @Column(name = "receipt_number", nullable = false, length = 40)
    private String receiptNumber;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Portion not yet applied to any invoice: resident credit / advance. */
    @Column(name = "unallocated_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal unallocatedAmount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    /** CASH, UPI, BANK_TRANSFER, CARD, OTHER, DEPOSIT_ADJUSTMENT. */
    @Column(nullable = false, length = 20)
    private String method;

    @Column(name = "transaction_reference", length = 100)
    private String transactionReference;

    /** Client-supplied retry key; unique per owner. See PaymentService.collect. */
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    /** RECEIVED or REVERSED. */
    @Column(nullable = false, length = 20)
    private String status = "RECEIVED";

    @Column(length = 500)
    private String notes;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversed_by")
    private Long reversedBy;

    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;
}
