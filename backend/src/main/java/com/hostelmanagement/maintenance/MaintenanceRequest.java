package com.hostelmanagement.maintenance;

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
@Table(name = "maintenance_requests")
public class MaintenanceRequest extends BaseEntity {

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "bed_id")
    private Long bedId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(length = 40)
    private String category;

    /** LOW, MEDIUM, HIGH, URGENT. */
    @Column(nullable = false, length = 20)
    private String priority;

    /** OPEN, ASSIGNED, IN_PROGRESS, ON_HOLD, RESOLVED, CLOSED. */
    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    /** When true and the request is not resolved/closed, the room (or bed) is unavailable for allocation. */
    @Column(name = "blocks_availability", nullable = false)
    private boolean blocksAvailability;

    @Column(name = "estimated_cost", precision = 12, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "actual_cost", precision = 12, scale = 2)
    private BigDecimal actualCost;

    @Column(name = "reported_by")
    private Long reportedBy;

    @Column(name = "assigned_staff_id")
    private Long assignedStaffId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;
}
