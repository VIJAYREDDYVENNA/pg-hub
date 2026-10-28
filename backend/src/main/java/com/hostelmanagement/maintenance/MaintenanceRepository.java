package com.hostelmanagement.maintenance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MaintenanceRepository extends JpaRepository<MaintenanceRequest, Long> {

    @Query("""
            SELECT m FROM MaintenanceRequest m WHERE m.hostelId IN :ids
            AND (:status IS NULL OR m.status = :status OR (:status = 'ACTIVE' AND m.status NOT IN ('RESOLVED','CLOSED')))
            AND (:priority IS NULL OR m.priority = :priority)
            AND (:roomId IS NULL OR m.roomId = :roomId)""")
    Page<MaintenanceRequest> search(@Param("ids") Collection<Long> ids, @Param("status") String status,
                                    @Param("priority") String priority, @Param("roomId") Long roomId,
                                    Pageable pageable);
}
