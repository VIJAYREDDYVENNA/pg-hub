package com.hostelmanagement.security;

import java.util.Set;

/**
 * Authenticated principal resolved from a JWT on every request. {@code ownerId} is null for the platform
 * super admin, who administers owner accounts but has no access to any owner's operational data.
 */
public record AuthUser(Long userId, Long ownerId, String email, String fullName,
                       Set<String> roles, Set<String> permissions) {

    public boolean isOwner() {
        return roles.contains("OWNER");
    }

    public boolean isSuperAdmin() {
        return roles.contains("SUPER_ADMIN");
    }

    public boolean has(String permission) {
        return isOwner() || permissions.contains(permission);
    }
}
