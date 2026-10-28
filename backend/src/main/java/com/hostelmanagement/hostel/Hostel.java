package com.hostelmanagement.hostel;

import com.hostelmanagement.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "hostels")
public class Hostel extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "hostel_type", nullable = false, length = 20)
    private HostelType hostelType;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false, length = 80)
    private String state;

    @Column(name = "pin_code", nullable = false, length = 10)
    private String pinCode;

    @Column(name = "contact_number", length = 20)
    private String contactNumber;

    @Column(length = 150)
    private String email;

    @Column(name = "manager_user_id")
    private Long managerUserId;

    @Column(name = "image_file_id")
    private Long imageFileId;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
