package com.hostelmanagement.security;

import com.hostelmanagement.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Central tenant/hostel isolation check. Owners can access all of their own hostels; other users only the
 * hostels explicitly granted to them. Records outside the caller's scope are reported as "not found" so that
 * IDs belonging to other owners cannot be probed.
 */
@Service
@RequiredArgsConstructor
public class HostelAccessService {

    private static final String CACHE_KEY = HostelAccessService.class.getName() + ".ids";

    private final JdbcTemplate jdbc;

    @SuppressWarnings("unchecked")
    public Set<Long> accessibleHostelIds() {
        AuthUser user = CurrentUser.get();
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            Object cached = attrs.getAttribute(CACHE_KEY, RequestAttributes.SCOPE_REQUEST);
            if (cached != null) {
                return (Set<Long>) cached;
            }
        }
        List<Long> ids = user.isOwner()
                ? jdbc.queryForList("SELECT id FROM hostels WHERE owner_id = ? AND deleted_at IS NULL ORDER BY id",
                Long.class, user.ownerId())
                : jdbc.queryForList("""
                        SELECT h.id FROM user_hostel_access a JOIN hostels h ON h.id = a.hostel_id
                        WHERE a.user_id = ? AND h.owner_id = ? AND h.deleted_at IS NULL ORDER BY h.id""",
                Long.class, user.userId(), user.ownerId());
        Set<Long> result = Set.copyOf(new LinkedHashSet<>(ids));
        if (attrs != null) {
            attrs.setAttribute(CACHE_KEY, result, RequestAttributes.SCOPE_REQUEST);
        }
        return result;
    }

    /** Invalidates the per-request cache (after creating a hostel within the same request). */
    public void evict() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            attrs.removeAttribute(CACHE_KEY, RequestAttributes.SCOPE_REQUEST);
        }
    }

    public boolean canAccess(Long hostelId) {
        return hostelId != null && accessibleHostelIds().contains(hostelId);
    }

    public void checkHostel(Long hostelId) {
        if (!canAccess(hostelId)) {
            throw ApiException.notFound("Hostel");
        }
    }

    /** Throws "not found" (named after the entity) when the hostel owning the entity is not accessible. */
    public void checkEntity(Long hostelId, String entityName) {
        if (!canAccess(hostelId)) {
            throw ApiException.notFound(entityName);
        }
    }

    /**
     * Resolves an optional hostel filter into a non-empty ID list for use in IN clauses.
     * Returns a sentinel (-1) when the user has no accessible hostels so queries return nothing.
     */
    public List<Long> scope(Long hostelId) {
        if (hostelId != null) {
            checkHostel(hostelId);
            return List.of(hostelId);
        }
        Set<Long> ids = accessibleHostelIds();
        return ids.isEmpty() ? List.of(-1L) : List.copyOf(ids);
    }

    public List<Long> scope(Collection<Long> hostelIds) {
        if (hostelIds == null || hostelIds.isEmpty()) {
            return scope((Long) null);
        }
        hostelIds.forEach(this::checkHostel);
        return List.copyOf(hostelIds);
    }
}
