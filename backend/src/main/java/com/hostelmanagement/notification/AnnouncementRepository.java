package com.hostelmanagement.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    @Query("""
            SELECT a FROM Announcement a WHERE a.ownerId = :ownerId
            AND (a.hostelId IS NULL OR a.hostelId IN :hostelIds)""")
    Page<Announcement> visible(@Param("ownerId") Long ownerId, @Param("hostelIds") Collection<Long> hostelIds,
                               Pageable pageable);
}
