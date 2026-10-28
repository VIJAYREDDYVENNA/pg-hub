package com.hostelmanagement.notification.whatsapp;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WhatsAppMessageRepository extends JpaRepository<WhatsAppMessage, Long> {

    @Query("""
            SELECT m FROM WhatsAppMessage m WHERE m.ownerId = :ownerId
            AND (m.hostelId IS NULL OR m.hostelId IN :hostelIds)
            AND (:hostelId IS NULL OR m.hostelId = :hostelId)
            AND (:residentId IS NULL OR m.residentId = :residentId)
            AND (:status IS NULL OR m.status = :status)""")
    Page<WhatsAppMessage> search(@Param("ownerId") Long ownerId, @Param("hostelIds") Collection<Long> hostelIds,
                                 @Param("hostelId") Long hostelId, @Param("residentId") Long residentId,
                                 @Param("status") String status, Pageable pageable);

    @Query("""
            SELECT m FROM WhatsAppMessage m WHERE m.status = 'FAILED' AND m.nextAttemptAt IS NOT NULL
            AND m.nextAttemptAt <= :now ORDER BY m.nextAttemptAt""")
    List<WhatsAppMessage> dueForRetry(@Param("now") Instant now);

    Optional<WhatsAppMessage> findFirstByProviderMessageId(String providerMessageId);
}
