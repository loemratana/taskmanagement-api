-- tasks:view:* was never seeded (see task-visibility-by-role.md). ADMIN currently
-- gets full task visibility only incidentally, via tasks:assign:all being treated
-- as equivalent in TaskAuthorizationService. Grant tasks:view:all explicitly so
-- admin's full view access no longer depends on that coincidence.

INSERT INTO permissions (id, name, module, action, is_active, description, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'tasks:view:all', 'TASK', 'view-all', true,
        'View all tasks in the system, regardless of department or assignee', now(), now())
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'tasks:view:all'
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
