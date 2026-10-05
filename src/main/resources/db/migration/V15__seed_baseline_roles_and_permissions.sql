-- Codifies the ADMIN/MANAGER/EMPLOYEE roles and the full permission catalog referenced by
-- com.ratana.task_management_api.security.role.Roles and
-- com.ratana.task_management_api.security.permission.Permissions. V1__initial_schema.sql is
-- empty (the schema comes from Hibernate ddl-auto:update in dev) and none of this baseline
-- was previously captured in a migration, so a fresh database/clone had empty roles and
-- permissions tables and no way to reach any @PreAuthorize-protected endpoint.
--
-- Every statement is additive and guarded with ON CONFLICT DO NOTHING, so on an existing
-- database (where this data already exists by hand) it is a no-op. The MANAGER/EMPLOYEE
-- grants below are reconstructed from what the codebase documents/relies on (see
-- docs/task-visibility-by-role.md and the V4/V9/V10/V12 migration comments) rather than read
-- back from any specific database, since that data lives only in each deployment's own DB —
-- review them against your actual environment if they don't match what you expect.

INSERT INTO roles (id, name, description, is_active, is_system_defined, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'ADMIN', 'Full access to every resource and administrative function', true, true, now(), now()),
    (gen_random_uuid(), 'MANAGER', 'Manages a team: department/team-scoped task assignment and review', true, true, now(), now()),
    (gen_random_uuid(), 'EMPLOYEE', 'Standard user: owns and works their own assigned tasks', true, true, now(), now())
ON CONFLICT (name) DO NOTHING;

INSERT INTO permissions (id, name, module, action, is_active, is_system_defined, description, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'users:create', 'USER', 'create', true, true, 'Create user accounts', now(), now()),
    (gen_random_uuid(), 'users:read', 'USER', 'read', true, true, 'Search/view user accounts', now(), now()),

    (gen_random_uuid(), 'roles:read', 'ROLE', 'read', true, true, 'View roles and their assigned permissions', now(), now()),
    (gen_random_uuid(), 'roles:create', 'ROLE', 'create', true, true, 'Create roles', now(), now()),
    (gen_random_uuid(), 'roles:update', 'ROLE', 'update', true, true, 'Rename/edit non-system roles', now(), now()),
    (gen_random_uuid(), 'roles:delete', 'ROLE', 'delete', true, true, 'Delete non-system roles', now(), now()),
    (gen_random_uuid(), 'roles:assign_permissions', 'ROLE', 'assign-permissions', true, true, 'Replace a role''s permission set', now(), now()),

    (gen_random_uuid(), 'permissions:read', 'PERMISSION', 'read', true, true, 'View permissions', now(), now()),
    (gen_random_uuid(), 'permissions:create', 'PERMISSION', 'create', true, true, 'Create permissions', now(), now()),
    (gen_random_uuid(), 'permissions:update', 'PERMISSION', 'update', true, true, 'Edit non-system permissions', now(), now()),
    (gen_random_uuid(), 'permissions:delete', 'PERMISSION', 'delete', true, true, 'Delete non-system permissions', now(), now()),

    (gen_random_uuid(), 'positions:read', 'POSITION', 'read', true, true, 'View job positions', now(), now()),

    (gen_random_uuid(), 'tasks:create', 'TASK', 'create', true, true, 'Create tasks', now(), now()),
    (gen_random_uuid(), 'tasks:read', 'TASK', 'read', true, true, 'Read task detail (baseline; view scope narrows further)', now(), now()),
    (gen_random_uuid(), 'tasks:update', 'TASK', 'update', true, true, 'Update any task in scope', now(), now()),
    (gen_random_uuid(), 'tasks:update:own', 'TASK', 'update-own', true, true, 'Update own assigned tasks', now(), now()),
    (gen_random_uuid(), 'tasks:delete', 'TASK', 'delete', true, true, 'Delete tasks', now(), now()),

    (gen_random_uuid(), 'tasks:view:all', 'TASK', 'view-all', true, true, 'View all tasks in the system, regardless of department or assignee', now(), now()),
    (gen_random_uuid(), 'tasks:view:department', 'TASK', 'view-department', true, true, 'View tasks within department', now(), now()),
    (gen_random_uuid(), 'tasks:view:team', 'TASK', 'view-team', true, true, 'View tasks assigned to direct reports, in addition to own tasks', now(), now()),
    (gen_random_uuid(), 'tasks:view:assigned', 'TASK', 'view-assigned', true, true, 'View tasks where the user is the assignee or creator', now(), now()),

    (gen_random_uuid(), 'tasks:assign:all', 'TASK', 'assign-all', true, true, 'Assign/update any task, regardless of department or team', now(), now()),
    (gen_random_uuid(), 'tasks:assign:department', 'TASK', 'assign-department', true, true, 'Assign/update tasks within department', now(), now()),
    (gen_random_uuid(), 'tasks:assign:team', 'TASK', 'assign-team', true, true, 'Assign/update tasks for direct reports (manager_id-driven), regardless of department', now(), now()),

    (gen_random_uuid(), 'tasks:transition:advance:own', 'TASK', 'transition-advance-own', true, true, 'Advance own assigned tasks through the workflow (e.g. To Do to In Progress)', now(), now()),
    (gen_random_uuid(), 'tasks:transition:review:all', 'TASK', 'transition-review-all', true, true, 'Approve or reject any task submitted for review', now(), now()),
    (gen_random_uuid(), 'tasks:transition:review:department', 'TASK', 'transition-review-department', true, true, 'Approve or reject tasks submitted for review within department', now(), now()),
    (gen_random_uuid(), 'tasks:transition:close:all', 'TASK', 'transition-close-all', true, true, 'Mark any approved task as done', now(), now()),
    (gen_random_uuid(), 'tasks:transition:close:department', 'TASK', 'transition-close-department', true, true, 'Mark approved tasks as done within department', now(), now()),
    (gen_random_uuid(), 'tasks:transition:cancel:all', 'TASK', 'transition-cancel-all', true, true, 'Cancel any task', now(), now()),
    (gen_random_uuid(), 'tasks:transition:cancel:department', 'TASK', 'transition-cancel-department', true, true, 'Cancel tasks within department', now(), now()),
    (gen_random_uuid(), 'tasks:transition:reopen:all', 'TASK', 'transition-reopen-all', true, true, 'Reopen a completed or cancelled task back to To Do', now(), now())
ON CONFLICT (name) DO NOTHING;

-- ADMIN: every permission in the catalog (this codebase already treats ADMIN as a superuser
-- via widespread hasRole('ADMIN') bypasses, so this makes that explicit at the permission level too).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

-- MANAGER: own CRUD + department/team-scoped assignment and workflow review/close/cancel.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN (
    'tasks:create', 'tasks:read', 'tasks:update',
    'tasks:view:team', 'tasks:assign:team',
    'tasks:transition:review:department', 'tasks:transition:close:department', 'tasks:transition:cancel:department'
) WHERE r.name = 'MANAGER'
ON CONFLICT DO NOTHING;

-- EMPLOYEE: own CRUD + workflow advance, matching V4's "minimum employee grant" and V10's view:assigned.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN (
    'tasks:create', 'tasks:read', 'tasks:update:own',
    'tasks:view:assigned', 'tasks:transition:advance:own'
) WHERE r.name = 'EMPLOYEE'
ON CONFLICT DO NOTHING;
