package com.hostelmanagement.room;

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
@Table(name = "rooms")
public class Room extends BaseEntity {

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "floor_id", nullable = false)
    private Long floorId;

    @Column(name = "room_number", nullable = false, length = 20)
    private String roomNumber;

    @Column(name = "room_type", nullable = false, length = 30)
    private String roomType;

    @Column(name = "monthly_rent_per_bed", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyRentPerBed;

    @Column(name = "security_deposit", nullable = false, precision = 12, scale = 2)
    private BigDecimal securityDeposit;

    @Column(length = 500)
    private String amenities;

    /** AVAILABLE, MAINTENANCE or INACTIVE. Occupancy is never stored here; it is derived from allocations. */
    @Column(nullable = false, length = 20)
    private String status = "AVAILABLE";

    /** Position on the floor plan: TOP / BOTTOM side of the corridor, or AUTO. */
    @Column(name = "plan_row", nullable = false, length = 10)
    private String planRow = "AUTO";

    /** Order of the room on the floor plan (left to right). */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
