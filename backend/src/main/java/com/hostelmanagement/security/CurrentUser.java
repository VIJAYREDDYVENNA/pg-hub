package com.hostelmanagement.security;

import com.hostelmanagement.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthUser get() {
        AuthUser user = getOrNull();
        if (user == null) {
            throw ApiException.unauthorized("Authentication required");
        }
        return user;
    }

    public static AuthUser getOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        return null;
    }

    public static void require(String permission) {
        if (!get().has(permission)) {
            throw ApiException.forbidden("You do not have permission to perform this action");
        }
    }
}
