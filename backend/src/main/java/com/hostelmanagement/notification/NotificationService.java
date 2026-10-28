package com.hostelmanagement.notification;

import com.hostelmanagement.auth.UserRepository;
import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.PageRequests;
import com.hostelmanagement.common.PageResponse;
import com.hostelmanagement.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbc;
    private final List<NotificationChannel> channels;

    public record NotificationDto(Long id, Long hostelId, String type, String title, String message, String link,
                                  boolean read, Instant createdAt) {
        static NotificationDto from(Notification n) {
            return new NotificationDto(n.getId(), n.getHostelId(), n.getType(), n.getTitle(), n.getMessage(),
                    n.getLink(), n.getReadAt() != null, n.getCreatedAt());
        }
    }

    public record PreferenceDto(String type, boolean inApp) {
    }

    /**
     * Notifies every user who can see the hostel (owners and users granted access). Duplicate notifications with
     * the same dedupe key for a user are ignored, so scheduled jobs can run repeatedly.
     */
    @Transactional
    public int notifyHostel(Long ownerId, Long hostelId, String type, String title, String message, String link,
                            String dedupeKey) {
        List<Long> userIds = hostelId == null ? userRepository.findOwnerUserIds(ownerId)
                : userRepository.findUserIdsWithHostelAccess(ownerId, hostelId);
        Set<Long> disabled = disabledUsers(userIds, type);
        int created = 0;
        Timestamp now = Timestamp.from(Instant.now());
        for (Long userId : userIds) {
            if (disabled.contains(userId)) {
                continue;
            }
            created += jdbc.update("""
                    INSERT IGNORE INTO notifications (owner_id, user_id, hostel_id, type, title, message, link,
                                                      dedupe_key, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", ownerId, userId, hostelId, type, truncate(title, 200),
                    truncate(message, 1000), link, dedupeKey, now);
        }
        if (created > 0 && !channels.isEmpty()) {
            log.debug("{} notification(s) of type {} created; extra channels: {}", created, type,
                    channels.stream().map(NotificationChannel::name).toList());
        }
        return created;
    }

    private Set<Long> disabledUsers(List<Long> userIds, String type) {
        Set<Long> result = new HashSet<>();
        if (userIds.isEmpty()) {
            return result;
        }
        String in = String.join(",", userIds.stream().map(String::valueOf).toList());
        jdbc.query("SELECT user_id FROM notification_preferences WHERE in_app = FALSE AND type = ? AND user_id IN (" + in + ")",
                rs -> {
                    result.add(rs.getLong(1));
                }, type);
        return result;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> list(boolean unreadOnly, Long hostelId, String type, int page, int size) {
        var pageable = PageRequests.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return PageResponse.of(repository.search(CurrentUser.get().userId(), unreadOnly, hostelId,
                type == null || type.isBlank() ? null : type, pageable), NotificationDto::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByUserIdAndReadAtIsNull(CurrentUser.get().userId());
    }

    @Transactional
    public void markRead(Long id) {
        Notification n = repository.findByIdAndUserId(id, CurrentUser.get().userId())
                .orElseThrow(() -> ApiException.notFound("Notification"));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
        }
    }

    @Transactional
    public int markAllRead() {
        return repository.markAllRead(CurrentUser.get().userId(), Instant.now());
    }

    @Transactional(readOnly = true)
    public List<PreferenceDto> preferences() {
        Map<String, Boolean> stored = new HashMap<>();
        jdbc.query("SELECT type, in_app FROM notification_preferences WHERE user_id = ?",
                rs -> {
                    stored.put(rs.getString(1), rs.getBoolean(2));
                }, CurrentUser.get().userId());
        return NotificationTypes.ALL.stream().map(t -> new PreferenceDto(t, stored.getOrDefault(t, true))).toList();
    }

    @Transactional
    public List<PreferenceDto> updatePreferences(List<PreferenceDto> prefs) {
        Long userId = CurrentUser.get().userId();
        for (PreferenceDto p : prefs) {
            if (!NotificationTypes.ALL.contains(p.type())) {
                throw ApiException.badRequest("Unknown notification type " + p.type());
            }
            jdbc.update("""
                    INSERT INTO notification_preferences (user_id, type, in_app) VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE in_app = VALUES(in_app)""", userId, p.type(), p.inApp());
        }
        return preferences();
    }

    private static String truncate(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
