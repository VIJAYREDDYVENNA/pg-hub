package com.hostelmanagement.bed;

import com.hostelmanagement.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "beds")
public class Bed extends BaseEntity {

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "bed_label", nullable = false, length = 20)
    private String bedLabel;

    /** AVAILABLE, MAINTENANCE or INACTIVE. Whether the bed is occupied is derived from bed_allocations. */
    @Column(nullable = false, length = 20)
    private String status = "AVAILABLE";

    @Column(name = "rent_override", precision = 12, scale = 2)
    private BigDecimal rentOverride;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
