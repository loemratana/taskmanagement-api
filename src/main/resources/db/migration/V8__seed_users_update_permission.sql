-- Permission guarding user profile/org updates (PUT /api/v1/users/{id}/org).
-- V7 granted ADMIN only the permissions that existed when it ran, so grant this one explicitly.

INSERT INTO permissions (id, name, module, action, is_active, description, created_at, updated_at)
VALUES (gen_random_uuid(), 'users:update', 'USER', 'update', true, 'Update users', now(), now())
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'users:update'
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
