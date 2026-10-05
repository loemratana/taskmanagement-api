package com.ratana.task_management_api.security.role;

/**
 * Single source of truth for the built-in role names checked by name anywhere in the app
 * (hasRole(...) in @PreAuthorize/SecurityConfig, and the few services that still branch on
 * role name directly). Values must match the seeded {@code roles.name} rows — see
 * V15__seed_baseline_roles_and_permissions.sql. These three are marked system-defined and
 * cannot be renamed or deleted via the Roles API.
 */
public final class Roles {
    private Roles() {}

    public static final String ADMIN = "ADMIN";
    public static final String MANAGER = "MANAGER";
    public static final String EMPLOYEE = "EMPLOYEE";
}
