package com.hostelmanagement.enquiry;

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
@Table(name = "enquiries")
public class Enquiry extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "hostel_id")
    private Long hostelId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 20)
    private String mobile;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String gender;

    @Column(name = "preferred_room_type", length = 30)
    private String preferredRoomType;

    @Column(name = "expected_admission_date")
    private LocalDate expectedAdmissionDate;

    @Column(precision = 12, scale = 2)
    private BigDecimal budget;

    @Column(length = 40)
    private String source;

    /** NEW, CONTACTED, FOLLOW_UP, BOOKED, CONVERTED, LOST. */
    @Column(nullable = false, length = 20)
    private String status = "NEW";

    @Column(name = "next_follow_up_date")
    private LocalDate nextFollowUpDate;

    @Column(length = 1000)
    private String notes;

    @Column(name = "converted_resident_id")
    private Long convertedResidentId;

    @Column(name = "whatsapp_opt_in", nullable = false)
    private boolean whatsappOptIn;

    @Column(name = "created_by")
    private Long createdBy;
}
