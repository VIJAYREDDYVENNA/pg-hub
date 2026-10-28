package com.hostelmanagement.payments;

import com.hostelmanagement.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Security deposit ledger for an admission. */
@Getter
@Setter
@Entity
@Table(name = "deposits")
public class Deposit extends BaseEntity {

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "resident_id", nullable = false)
    private Long residentId;

    @Column(name = "admission_id", nullable = false)
    private Long admissionId;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "amount_required", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountRequired;

    @Column(name = "amount_received", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountReceived;

    @Column(name = "amount_refunded", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountRefunded;

    @Column(name = "amount_adjusted", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountAdjusted;

    /** PENDING, HELD, PARTIALLY_REFUNDED, REFUNDED, ADJUSTED. */
    @Column(nullable = false, length = 25)
    private String status;

    public BigDecimal held() {
        return amountReceived.subtract(amountRefunded).subtract(amountAdjusted);
    }

    public void refreshStatus() {
        if (amountReceived.signum() == 0) {
            status = "PENDING";
        } else if (held().signum() > 0) {
            status = amountRefunded.signum() > 0 ? "PARTIALLY_REFUNDED" : "HELD";
        } else {
            status = amountAdjusted.compareTo(amountRefunded) > 0 ? "ADJUSTED" : "REFUNDED";
        }
    }
}
