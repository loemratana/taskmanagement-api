-- RoleController/PermissionController let admins rename or soft-delete any role or
-- permission row at runtime, but Java code references a fixed set of role names
-- (com.ratana.task_management_api.security.role.Roles) and permission names
-- (com.ratana.task_management_api.security.permission.Permissions) by string literal.
-- Renaming or deleting one of those rows silently breaks the @PreAuthorize check that
-- depends on it, with no compile-time signal. is_system_defined lets RoleServiceImpl /
-- PermissionServiceImpl refuse rename/delete on rows the code depends on, while still
-- allowing admins to freely create/edit/delete any custom role or permission they add.

ALTER TABLE roles ADD COLUMN IF NOT EXISTS is_system_defined BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS is_system_defined BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE roles SET is_system_defined = TRUE WHERE name IN ('ADMIN', 'MANAGER', 'EMPLOYEE');

UPDATE permissions SET is_system_defined = TRUE WHERE name IN (
    'users:create', 'users:read',
    'roles:read', 'roles:create', 'roles:update', 'roles:delete', 'roles:assign_permissions',
    'permissions:read', 'permissions:create', 'permissions:update', 'permissions:delete',
    'positions:read',
    'tasks:create', 'tasks:read', 'tasks:update', 'tasks:update:own', 'tasks:delete',
    'tasks:view:all', 'tasks:view:department', 'tasks:view:team', 'tasks:view:assigned',
    'tasks:assign:all', 'tasks:assign:department', 'tasks:assign:team',
    'tasks:transition:advance:own',
    'tasks:transition:review:all', 'tasks:transition:review:department',
    'tasks:transition:close:all', 'tasks:transition:close:department',
    'tasks:transition:cancel:all', 'tasks:transition:cancel:department',
    'tasks:transition:reopen:all'
);
