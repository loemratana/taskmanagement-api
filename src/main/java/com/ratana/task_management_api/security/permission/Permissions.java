package com.ratana.task_management_api.security.permission;

/**
 * Single source of truth for every permission-authority string checked anywhere in the app
 * (@PreAuthorize annotations, {@code TaskPermissionService}, resource-scoped services).
 * Referencing these constants — instead of repeating string literals — means a rename here
 * is the only place that needs to change, and a typo fails to compile instead of silently
 * denying or allowing access at runtime.
 *
 * <p>Convention: {@code module:action[:scope]}, module names plural, hyphenated multi-word actions.
 *
 * <p>Values must stay in sync with the seeded {@code permissions.name} rows — see
 * V13__standardize_permission_names.sql and V15__seed_baseline_roles_and_permissions.sql.
 */
public final class Permissions {
    private Permissions() {}

    // ---- Users ----
    public static final String USERS_CREATE = "users:create";
    public static final String USERS_READ = "users:read";

    // ---- Roles ----
    public static final String ROLES_READ = "roles:read";
    public static final String ROLES_CREATE = "roles:create";
    public static final String ROLES_UPDATE = "roles:update";
    public static final String ROLES_DELETE = "roles:delete";
    public static final String ROLES_ASSIGN_PERMISSIONS = "roles:assign_permissions";

    // ---- Permissions ----
    public static final String PERMISSIONS_READ = "permissions:read";
    public static final String PERMISSIONS_CREATE = "permissions:create";
    public static final String PERMISSIONS_UPDATE = "permissions:update";
    public static final String PERMISSIONS_DELETE = "permissions:delete";

    // ---- Positions ----
    public static final String POSITIONS_READ = "positions:read";

    // ---- Tasks: CRUD ----
    public static final String TASK_CREATE = "tasks:create";
    public static final String TASK_READ = "tasks:read";
    public static final String TASK_UPDATE = "tasks:update";
    public static final String TASK_UPDATE_OWN = "tasks:update:own";
    public static final String TASK_DELETE = "tasks:delete";

    // ---- Tasks: view ----
    public static final String TASK_VIEW_ALL = "tasks:view:all";
    public static final String TASK_VIEW_DEPARTMENT = "tasks:view:department";
    public static final String TASK_VIEW_TEAM = "tasks:view:team";
    public static final String TASK_VIEW_ASSIGNED = "tasks:view:assigned";

    // ---- Tasks: assignment ----
    public static final String TASK_ASSIGN_ALL = "tasks:assign:all";
    public static final String TASK_ASSIGN_DEPARTMENT = "tasks:assign:department";
    public static final String TASK_ASSIGN_TEAM = "tasks:assign:team";

    // ---- Tasks: workflow transitions ----
    public static final String TASK_TRANSITION_ADVANCE_OWN = "tasks:transition:advance:own";

    public static final String TASK_TRANSITION_REVIEW_ALL = "tasks:transition:review:all";
    public static final String TASK_TRANSITION_REVIEW_DEPARTMENT = "tasks:transition:review:department";

    public static final String TASK_TRANSITION_CLOSE_ALL = "tasks:transition:close:all";
    public static final String TASK_TRANSITION_CLOSE_DEPARTMENT = "tasks:transition:close:department";

    public static final String TASK_TRANSITION_CANCEL_ALL = "tasks:transition:cancel:all";
    public static final String TASK_TRANSITION_CANCEL_DEPARTMENT = "tasks:transition:cancel:department";
    public static final String TASK_TRANSITION_REOPEN_ALL = "tasks:transition:reopen:all";
}
