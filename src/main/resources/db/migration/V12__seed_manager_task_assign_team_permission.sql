-- Complements V10 (tasks:view:team): grants MANAGER the matching assign-scope
-- permission. TaskAuthorizationService.canAssignTask()/canUpdateTask() had no
-- TASK_ASSIGN_TEAM branch at all until now, so this grant was previously inert
-- even where it existed — see the code fix alongside this migration.

INSERT INTO permissions (id, name, module, action, is_active, description, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'tasks:assign:team', 'TASK', 'assign-team', true,
        'Assign/update tasks for direct reports (manager_id-driven), regardless of department', now(), now())
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'tasks:assign:team'
WHERE r.name = 'MANAGER'
ON CONFLICT DO NOTHING;
