package com.hostelmanagement.admission;

import com.hostelmanagement.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** A period during which an admission occupied a bed. end_date NULL = current allocation. */
@Getter
@Setter
@Entity
@Table(name = "bed_allocations")
public class BedAllocation extends BaseEntity {

    @Column(name = "admission_id", nullable = false)
    private Long admissionId;

    @Column(name = "bed_id", nullable = false)
    private Long bedId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** TRANSFER or CHECKOUT. */
    @Column(name = "end_reason", length = 20)
    private String endReason;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;
}
