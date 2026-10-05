-- V16 tightened MANAGER to team-scope only (tasks:view:team / tasks:assign:team), per
-- docs/task-visibility-by-role.md's suggested seed. But this deployment's actual org data is
-- department-based, not manager_id-based: every employee in a department reports to the
-- System Admin user, not to their department's MANAGER, so the manager_id direct-report
-- relationship team-scope depends on is empty for every manager. Department is the grouping
-- that's actually populated (actor.department_id == task.department_id / target.department_id).
--
-- TaskAuthorizationService already has full department-scope branches for assign/view/update
-- (canAssignTask, canViewTask, canUpdateTask, resolveViewScope all check *_DEPARTMENT), so this
-- is purely a data grant — team scope stays granted too and simply stays dormant unless
-- manager_id hierarchies are set up later.

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN (
    'tasks:view:department', 'tasks:assign:department'
) WHERE r.name = 'MANAGER'
ON CONFLICT DO NOTHING;
