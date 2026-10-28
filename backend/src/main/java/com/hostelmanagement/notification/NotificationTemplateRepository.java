package com.hostelmanagement.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    @Query("""
            SELECT t FROM NotificationTemplate t WHERE t.channel = 'WHATSAPP'
            AND (t.ownerId IS NULL OR t.ownerId = :ownerId) ORDER BY t.code, t.ownerId DESC NULLS LAST""")
    List<NotificationTemplate> visible(@Param("ownerId") Long ownerId);

    Optional<NotificationTemplate> findByOwnerIdAndCodeAndChannel(Long ownerId, String code, String channel);
}
