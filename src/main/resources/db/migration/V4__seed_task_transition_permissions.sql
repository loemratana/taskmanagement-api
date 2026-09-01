-- The TaskPermissions constants for workflow transitions were never seeded,
-- so TaskAuthorizationService.canTransition() rejected every non-cancel
-- transition for every role, including ADMIN.

INSERT INTO permissions (id, name, module, action, is_active, description, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'tasks:transition:advance:own', 'TASK', 'transition-advance-own', true,
        'Advance own assigned tasks through the workflow (e.g. To Do to In Progress)', now(), now()),
    (gen_random_uuid(), 'tasks:transition:review:all', 'TASK', 'transition-review-all', true,
        'Approve or reject any task submitted for review', now(), now()),
    (gen_random_uuid(), 'tasks:transition:review:department', 'TASK', 'transition-review-department', true,
        'Approve or reject tasks submitted for review within department', now(), now()),
    (gen_random_uuid(), 'tasks:transition:close:all', 'TASK', 'transition-close-all', true,
        'Mark any approved task as done', now(), now()),
    (gen_random_uuid(), 'tasks:transition:close:department', 'TASK', 'transition-close-department', true,
        'Mark approved tasks as done within department', now(), now()),
    (gen_random_uuid(), 'tasks:transition:cancel:all', 'TASK', 'transition-cancel-all', true,
        'Cancel any task', now(), now()),
    (gen_random_uuid(), 'tasks:transition:cancel:department', 'TASK', 'transition-cancel-department', true,
        'Cancel tasks within department', now(), now()),
    (gen_random_uuid(), 'tasks:transition:reopen:all', 'TASK', 'transition-reopen-all', true,
        'Reopen a completed or cancelled task back to To Do', now(), now())
ON CONFLICT (name) DO NOTHING;

-- Employees can advance and edit their own assigned tasks — explicitly called out
-- as the minimum employee grant in task-visibility-by-role.md.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('tasks:transition:advance:own', 'tasks:update:own')
WHERE r.name = 'EMPLOYEE'
ON CONFLICT DO NOTHING;
