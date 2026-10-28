package com.hostelmanagement.booking;

import com.hostelmanagement.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A reservation of a specific bed for a future admission. */
@Getter
@Setter
@Entity
@Table(name = "bookings")
public class Booking extends BaseEntity {

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "bed_id", nullable = false)
    private Long bedId;

    @Column(name = "enquiry_id")
    private Long enquiryId;

    @Column(name = "resident_id")
    private Long residentId;

    @Column(name = "guest_name", nullable = false, length = 150)
    private String guestName;

    @Column(name = "guest_mobile", nullable = false, length = 20)
    private String guestMobile;

    @Column(name = "expected_admission_date", nullable = false)
    private LocalDate expectedAdmissionDate;

    @Column(name = "hold_until")
    private LocalDate holdUntil;

    /** Informational only: advance money is recorded as a payment once the guest becomes a resident. */
    @Column(name = "advance_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal advanceAmount = BigDecimal.ZERO;

    /** RESERVED, CONVERTED, CANCELLED, EXPIRED. */
    @Column(nullable = false, length = 20)
    private String status = "RESERVED";

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;
}
