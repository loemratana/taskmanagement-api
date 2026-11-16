-- Managers work on their own tasks plus their direct reports' tasks ("team" scope).
-- V7 granted MANAGER tasks:view:team and tasks:assign:team, which the code now
-- honours, but the workflow grants were department-wide. Add team-scoped
-- review/close/cancel permissions and move MANAGER onto them.

INSERT INTO permissions (id, name, module, action, is_active, description, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'tasks:transition:review:team', 'TASK', 'transition-review-team', true,
        'Approve or reject tasks submitted for review by direct reports', now(), now()),
    (gen_random_uuid(), 'tasks:transition:close:team', 'TASK', 'transition-close-team', true,
        'Mark approved own or direct reports'' tasks as done', now(), now()),
    (gen_random_uuid(), 'tasks:transition:cancel:team', 'TASK', 'transition-cancel-team', true,
        'Cancel own or direct reports'' tasks', now(), now())
ON CONFLICT (name) DO NOTHING;

-- ADMIN keeps "every permission" (V7); it already has the :all scopes.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
    'tasks:transition:review:team',
    'tasks:transition:close:team',
    'tasks:transition:cancel:team'
)
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN (
    'tasks:view:team',
    'tasks:assign:team',
    'tasks:transition:review:team',
    'tasks:transition:close:team',
    'tasks:transition:cancel:team'
)
WHERE r.name = 'MANAGER'
ON CONFLICT DO NOTHING;

-- Drop MANAGER's department-wide grants so team scope is the only scope it has.
DELETE FROM role_permissions rp
USING roles r, permissions p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.name = 'MANAGER'
  AND p.name IN (
    'tasks:view:department',
    'tasks:assign:department',
    'tasks:transition:review:department',
    'tasks:transition:close:department',
    'tasks:transition:cancel:department'
  );
