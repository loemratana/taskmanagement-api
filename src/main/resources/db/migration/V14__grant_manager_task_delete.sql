-- Managers can archive (soft-delete) tasks. TaskAuthorizationService.canArchive also requires
-- the task to be visible to them, so this stays within their team/department scope.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'tasks:delete'
WHERE r.name = 'MANAGER'
ON CONFLICT DO NOTHING;
