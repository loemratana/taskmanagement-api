package com.ratana.task_management_api.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;



public final class SecurityUtils {
    private SecurityUtils() {
    }

    public static UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication ==null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {

            throw new IllegalArgumentException("No authenticated user in security context");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal p) {

            return p;

        }

        throw new IllegalStateException(
                "Principal type %s does not implement TmsUserPrincipal. Have your existing JWT principal class implement it (getUserId/getDepartmentId/getPermissions)."
                        .formatted(principal.getClass().getName()));
    }

    public static UUID currentUserId() {
        return getCurrentUser().getId();
    }

}
