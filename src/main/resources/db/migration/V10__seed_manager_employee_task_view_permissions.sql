-- Continues V9: seed the remaining tasks:view:* permissions per the "Suggested
-- role seed" table in task-visibility-by-role.md. Note both MANAGER and EMPLOYEE
-- already hold tasks:assign:all in this dev seed, which resolveViewScope() also
-- treats as ViewScope.ALL — so this grant is correct but currently dormant until
-- that broader grant is revisited; it does not by itself narrow anyone's scope.

INSERT INTO permissions (id, name, module, action, is_active, description, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'tasks:view:team', 'TASK', 'view-team', true,
        'View tasks assigned to direct reports, in addition to own tasks', now(), now()),
    (gen_random_uuid(), 'tasks:view:assigned', 'TASK', 'view-assigned', true,
        'View tasks where the user is the assignee or creator', now(), now())
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'tasks:view:team'
WHERE r.name = 'MANAGER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'tasks:view:assigned'
WHERE r.name = 'EMPLOYEE'
ON CONFLICT DO NOTHING;
