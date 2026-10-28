package com.hostelmanagement.enquiry;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface EnquiryRepository extends JpaRepository<Enquiry, Long> {

    /** Enquiries of the owner that are either unassigned to a hostel or assigned to an accessible hostel. */
    @Query("""
            SELECT e FROM Enquiry e WHERE e.ownerId = :ownerId
              AND (e.hostelId IS NULL OR e.hostelId IN :hostelIds)
              AND (:hostelId IS NULL OR e.hostelId = :hostelId)
              AND (:status IS NULL OR e.status = :status)
              AND (:q IS NULL OR LOWER(e.fullName) LIKE :q OR e.mobile LIKE :q)""")
    Page<Enquiry> search(@Param("ownerId") Long ownerId, @Param("hostelIds") Collection<Long> hostelIds,
                         @Param("hostelId") Long hostelId, @Param("status") String status, @Param("q") String q,
                         Pageable pageable);
}
