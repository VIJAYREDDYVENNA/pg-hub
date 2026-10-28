package com.hostelmanagement.maintenance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** History entry: assignment or status change of a maintenance request. */
@Getter
@Setter
@Entity
@Table(name = "maintenance_assignments")
public class MaintenanceAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "staff_id")
    private Long staffId;

    @Column(name = "status_from", length = 20)
    private String statusFrom;

    @Column(name = "status_to", nullable = false, length = 20)
    private String statusTo;

    @Column(length = 1000)
    private String note;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
