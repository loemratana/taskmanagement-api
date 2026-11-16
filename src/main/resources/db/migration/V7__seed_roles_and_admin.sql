-- Seed the base roles, the permissions referenced in code, their role grants,
-- and a bootstrap admin account. Idempotent: safe on databases that already
-- have some of these rows.

INSERT INTO roles (id, name, description, is_active, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'ADMIN', 'Full system access', true, now(), now()),
    (gen_random_uuid(), 'MANAGER', 'Manages a team and reviews their tasks', true, now(), now()),
    (gen_random_uuid(), 'EMPLOYEE', 'Works on own assigned tasks', true, now(), now())
ON CONFLICT (name) DO NOTHING;

-- Workflow transition permissions are seeded in V4.
INSERT INTO permissions (id, name, module, action, is_active, description, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'tasks:create', 'TASK', 'create', true, 'Create tasks', now(), now()),
    (gen_random_uuid(), 'tasks:read', 'TASK', 'read', true, 'Read tasks', now(), now()),
    (gen_random_uuid(), 'tasks:update', 'TASK', 'update', true, 'Update any visible task', now(), now()),
    (gen_random_uuid(), 'tasks:update:own', 'TASK', 'update-own', true, 'Update own assigned tasks', now(), now()),
    (gen_random_uuid(), 'tasks:delete', 'TASK', 'delete', true, 'Delete tasks', now(), now()),
    (gen_random_uuid(), 'tasks:view:all', 'TASK', 'view-all', true, 'View all tasks', now(), now()),
    (gen_random_uuid(), 'tasks:view:department', 'TASK', 'view-department', true, 'View tasks within department', now(), now()),
    (gen_random_uuid(), 'tasks:view:team', 'TASK', 'view-team', true, 'View own tasks and direct reports'' tasks', now(), now()),
    (gen_random_uuid(), 'tasks:view:assigned', 'TASK', 'view-assigned', true, 'View own assigned or created tasks', now(), now()),
    (gen_random_uuid(), 'tasks:assign:all', 'TASK', 'assign-all', true, 'Assign tasks to anyone', now(), now()),
    (gen_random_uuid(), 'tasks:assign:department', 'TASK', 'assign-department', true, 'Assign tasks within department', now(), now()),
    (gen_random_uuid(), 'tasks:assign:team', 'TASK', 'assign-team', true, 'Assign tasks to direct reports', now(), now()),
    (gen_random_uuid(), 'users:create', 'USER', 'create', true, 'Create users', now(), now()),
    (gen_random_uuid(), 'users:view', 'USER', 'view', true, 'View users', now(), now()),
    (gen_random_uuid(), 'role:read', 'ROLE', 'read', true, 'Read roles', now(), now()),
    (gen_random_uuid(), 'role:create', 'ROLE', 'create', true, 'Create roles', now(), now()),
    (gen_random_uuid(), 'role:update', 'ROLE', 'update', true, 'Update roles', now(), now()),
    (gen_random_uuid(), 'role:delete', 'ROLE', 'delete', true, 'Delete roles', now(), now()),
    (gen_random_uuid(), 'role:assign_permissions', 'ROLE', 'assign-permissions', true, 'Assign permissions to roles', now(), now()),
    (gen_random_uuid(), 'permission:read', 'PERMISSION', 'read', true, 'Read permissions', now(), now()),
    (gen_random_uuid(), 'permission:create', 'PERMISSION', 'create', true, 'Create permissions', now(), now()),
    (gen_random_uuid(), 'permission:update', 'PERMISSION', 'update', true, 'Update permissions', now(), now()),
    (gen_random_uuid(), 'permission:delete', 'PERMISSION', 'delete', true, 'Delete permissions', now(), now()),
    (gen_random_uuid(), 'position:read', 'POSITION', 'read', true, 'Read positions', now(), now())
ON CONFLICT (name) DO NOTHING;

-- ADMIN gets every permission.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
    'tasks:create', 'tasks:read', 'tasks:update',
    'tasks:view:team', 'tasks:assign:team',
    'tasks:transition:advance:own',
    'tasks:transition:review:department',
    'tasks:transition:close:department',
    'tasks:transition:cancel:department',
    'users:view', 'position:read'
)
WHERE r.name = 'MANAGER'
ON CONFLICT DO NOTHING;

-- Re-applies the EMPLOYEE grant from V4, which was a no-op before the role existed.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
    'tasks:read', 'tasks:view:assigned', 'tasks:update:own',
    'tasks:transition:advance:own', 'position:read'
)
WHERE r.name = 'EMPLOYEE'
ON CONFLICT DO NOTHING;

-- Bootstrap admin. Password: Admin@123 (BCrypt) — change it after first login.
INSERT INTO users (id, full_name, email, password_hash, status, is_active, created_at, updated_at)
VALUES (gen_random_uuid(), 'System Administrator', 'admin@example.com',
        '$2a$10$aRv7Yl/EejvRy8HQDRVQte6qilGYQ41FGEEHPzpQoIjBdEF2qOuFK',
        'ACTIVE', true, now(), now())
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'ADMIN'
WHERE u.email = 'admin@example.com'
ON CONFLICT DO NOTHING;
