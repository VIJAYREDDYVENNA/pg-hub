package com.hostelmanagement.staff;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    @Query("""
            SELECT s FROM Staff s WHERE s.ownerId = :ownerId AND (s.hostelId IS NULL OR s.hostelId IN :hostelIds)
            AND (:hostelId IS NULL OR s.hostelId = :hostelId) ORDER BY s.status, s.fullName""")
    List<Staff> visible(@Param("ownerId") Long ownerId, @Param("hostelIds") Collection<Long> hostelIds,
                        @Param("hostelId") Long hostelId);
}
