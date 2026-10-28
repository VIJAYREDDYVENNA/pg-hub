package com.hostelmanagement.staff;

import com.hostelmanagement.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An employee record. Staff may optionally have a login (user_id). */
@Getter
@Setter
@Entity
@Table(name = "staff")
public class Staff extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "hostel_id")
    private Long hostelId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 20)
    private String mobile;

    @Column(nullable = false, length = 60)
    private String designation;

    @Column(name = "monthly_salary", precision = 12, scale = 2)
    private BigDecimal monthlySalary;

    @Column(name = "joined_on")
    private LocalDate joinedOn;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
}
