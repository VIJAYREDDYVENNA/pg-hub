package com.hostelmanagement.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            SELECT n FROM Notification n WHERE n.userId = :userId
            AND (:unreadOnly = false OR n.readAt IS NULL)
            AND (:hostelId IS NULL OR n.hostelId = :hostelId)
            AND (:type IS NULL OR n.type = :type)""")
    Page<Notification> search(@Param("userId") Long userId, @Param("unreadOnly") boolean unreadOnly,
                              @Param("hostelId") Long hostelId, @Param("type") String type, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /**
     * Deletes up to {@code batchSize} notifications created at or before {@code cutoff}, whatever their owner or read
     * state. Age is the original creation time only. Notifications are not referenced by any other table.
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM notifications WHERE created_at <= :cutoff ORDER BY created_at LIMIT :batchSize",
            nativeQuery = true)
    int deleteExpiredBatch(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);
}
