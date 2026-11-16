package com.ratana.task_management_api.infrastructure.security;

import com.ratana.task_management_api.user.enums.UserStatus;
import com.ratana.task_management_api.rbac.entity.Permission;
import com.ratana.task_management_api.rbac.entity.Role;
import com.ratana.task_management_api.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserPrincipalTest {

    private static Permission permission(String name, boolean active) {
        return Permission.builder().id(UUID.randomUUID()).name(name).active(active).build();
    }

    private static Role role(String name, boolean active, Permission... permissions) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName(name);
        role.setActive(active);
        role.setPermissions(Set.of(permissions));
        return role;
    }

    private static User user(UserStatus status, boolean active, Role... roles) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("m@example.com");
        user.setStatus(status);
        user.setActive(active);
        user.setRoles(Set.of(roles));
        return user;
    }

    @Test
    @DisplayName("TC-RBAC-05: deactivated roles and permissions grant nothing (DEF-47)")
    void inactiveRolesAndPermissions_areIgnored() {
        Role manager = role("MANAGER", true,
                permission("tasks:create", true),
                permission("tasks:delete", false));
        Role legacy = role("LEGACY", false, permission("users:update", true));

        UserPrincipal principal = UserPrincipal.createUserPrincipal(user(UserStatus.ACTIVE, true, manager, legacy));

        assertThat(principal.getRoles()).containsExactly("MANAGER");
        assertThat(principal.getPermissions()).containsExactly("tasks:create");
    }

    @Test
    @DisplayName("enabled only when status is ACTIVE and is_active is true (DEF-04)")
    void enabled_requiresBothFlags() {
        assertThat(UserPrincipal.createUserPrincipal(user(UserStatus.ACTIVE, true)).isEnabled()).isTrue();
        assertThat(UserPrincipal.createUserPrincipal(user(UserStatus.ACTIVE, false)).isEnabled()).isFalse();
        assertThat(UserPrincipal.createUserPrincipal(user(UserStatus.INACTIVE, true)).isEnabled()).isFalse();
        assertThat(UserPrincipal.createUserPrincipal(user(UserStatus.SUSPENDED, true)).isEnabled()).isFalse();
    }
}
