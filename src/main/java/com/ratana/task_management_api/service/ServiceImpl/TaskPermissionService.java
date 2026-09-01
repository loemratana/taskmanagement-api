package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.security.SecurityUtils;
import com.ratana.task_management_api.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Thin permission checker over JWT authorities.
 * Resource-scoped rules (department, assignee) belong in {@link TaskAuthorizationService}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskPermissionService {

    public boolean hasPermission(UserPrincipal user, String permission) {
        if (user == null || permission == null || permission.isBlank()) {
            return false;
        }
        boolean allowed = user.hasPermission(permission);
        if (!allowed) {
            log.debug("Permission denied: user={} missing={}", user.getId(), permission);
        }
        return allowed;
    }

    public boolean hasCurrentUserPermission(String permission) {
        return hasPermission(SecurityUtils.getCurrentUser(), permission);
    }

    public boolean hasAnyPermission(UserPrincipal user, String... permissions) {
        if (user == null || permissions == null || permissions.length == 0) {
            return false;
        }
        return user.hasAnyPermission(permissions);
    }

    public boolean hasAnyPermission(String... permissions) {
        try {
            return hasAnyPermission(SecurityUtils.getCurrentUser(), permissions);
        } catch (Exception e) {
            return false;
        }
    }
}
