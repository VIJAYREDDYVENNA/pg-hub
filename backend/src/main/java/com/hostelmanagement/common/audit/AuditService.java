package com.hostelmanagement.common.audit;

import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

/** Records sensitive actions. Runs inside the caller's transaction so audit and change commit together. */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void log(String action, String entityType, Long entityId, String details) {
        AuthUser user = CurrentUser.getOrNull();
        log(user == null ? null : user.ownerId(), user == null ? null : user.userId(), action, entityType, entityId, details);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void log(Long ownerId, Long userId, String action, String entityType, Long entityId, String details) {
        AuditLog entry = new AuditLog();
        entry.setOwnerId(ownerId);
        entry.setUserId(userId);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDetails(details == null ? null : details.length() > 2000 ? details.substring(0, 2000) : details);
        entry.setIpAddress(currentIp());
        entry.setCreatedAt(Instant.now());
        repository.save(entry);
    }

    public static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            return req.getRemoteAddr();
        }
        return null;
    }
}
