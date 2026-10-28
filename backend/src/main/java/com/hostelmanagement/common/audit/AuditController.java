package com.hostelmanagement.common.audit;

import com.hostelmanagement.common.PageRequests;
import com.hostelmanagement.common.PageResponse;
import com.hostelmanagement.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;

@Tag(name = "Audit log")
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository repository;

    public record AuditLogDto(Long id, Long userId, String action, String entityType, Long entityId,
                              String details, String ipAddress, Instant createdAt) {
    }

    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    public PageResponse<AuditLogDto> list(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "25") int size) {
        var pageable = PageRequests.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(repository.findByOwnerId(CurrentUser.get().ownerId(), pageable),
                a -> new AuditLogDto(a.getId(), a.getUserId(), a.getAction(), a.getEntityType(), a.getEntityId(),
                        a.getDetails(), a.getIpAddress(), a.getCreatedAt()));
    }
}
