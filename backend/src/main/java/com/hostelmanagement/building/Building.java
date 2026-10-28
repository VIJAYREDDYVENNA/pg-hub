package com.hostelmanagement.building;

import com.hostelmanagement.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "buildings")
public class Building extends BaseEntity {

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
