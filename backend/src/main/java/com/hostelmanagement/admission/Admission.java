package com.hostelmanagement.admission;

import com.hostelmanagement.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "admissions")
public class Admission extends BaseEntity {

    @Column(name = "resident_id", nullable = false)
    private Long residentId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "admission_date", nullable = false)
    private LocalDate admissionDate;

    @Column(name = "expected_checkout_date")
    private LocalDate expectedCheckoutDate;

    /** True when the checkout date was confirmed by the resident; false = estimate. */
    @Column(name = "checkout_confirmed", nullable = false)
    private boolean checkoutConfirmed;

    @Column(name = "actual_checkout_date")
    private LocalDate actualCheckoutDate;

    @Column(name = "monthly_rent", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyRent;

    @Column(name = "security_deposit", nullable = false, precision = 12, scale = 2)
    private BigDecimal securityDeposit;

    @Column(name = "billing_cycle", nullable = false, length = 20)
    private String billingCycle = "MONTHLY";

    @Column(name = "cycle_months", nullable = false)
    private int cycleMonths = 1;

    /** ACTIVE, CHECKED_OUT or CANCELLED. */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "checkout_notes", length = 1000)
    private String checkoutNotes;

    @Column(length = 1000)
    private String notes;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "created_by")
    private Long createdBy;
}
