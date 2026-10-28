package com.hostelmanagement.resident;

import com.hostelmanagement.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "residents")
public class Resident extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** Hostel the resident currently belongs to (updated on admission / cross-hostel transfer). */
    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "photo_file_id")
    private Long photoFileId;

    @Column(nullable = false, length = 20)
    private String mobile;

    @Column(name = "alternate_mobile", length = 20)
    private String alternateMobile;

    @Column(length = 150)
    private String email;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 20)
    private String gender;

    @Column(length = 500)
    private String address;

    @Column(name = "emergency_contact_name", length = 150)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(name = "id_document_type", length = 40)
    private String idDocumentType;

    @Column(name = "id_document_number", length = 60)
    private String idDocumentNumber;

    @Column(name = "guardian_name", length = 150)
    private String guardianName;

    @Column(name = "guardian_phone", length = 20)
    private String guardianPhone;

    @Column(name = "guardian_relation", length = 40)
    private String guardianRelation;

    @Column(length = 100)
    private String occupation;

    /** REGISTERED (no stay yet), ACTIVE (currently admitted), CHECKED_OUT, INACTIVE. */
    @Column(nullable = false, length = 20)
    private String status = "REGISTERED";

    @Column(name = "whatsapp_opt_in", nullable = false)
    private boolean whatsappOptIn;

    @Column(name = "whatsapp_opt_in_at")
    private Instant whatsappOptInAt;

    @Column(length = 1000)
    private String notes;
}
