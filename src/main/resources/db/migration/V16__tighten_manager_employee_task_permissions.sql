-- MANAGER and EMPLOYEE had accumulated grants far beyond docs/task-visibility-by-role.md's
-- documented model — MANAGER held roles:*, permissions:*, department:*, position:*, users:*
-- (nearly every permission in the system, barely distinguishable from ADMIN), and EMPLOYEE
-- held nearly every tasks:* permission including tasks:delete, tasks:assign:all, and
-- tasks:transition:cancel:all/close:all/review:all/reopen:all — well beyond "own tasks only".
-- This revokes everything outside each role's documented scope. Plain DELETEs are idempotent
-- (re-running finds nothing left to remove).
--
-- Target sets (see docs/task-visibility-by-role.md "Suggested role seed" + "Team vs
-- department"): MANAGER is team-scoped, not department- or org-admin-scoped; EMPLOYEE is
-- own-tasks-only.

DELETE FROM role_permissions
WHERE role_id = (SELECT id FROM roles WHERE name = 'MANAGER')
  AND permission_id NOT IN (
      SELECT id FROM permissions WHERE name IN (
          'tasks:create', 'tasks:read', 'tasks:update',
          'tasks:view:team', 'tasks:assign:team',
          'tasks:transition:review:department', 'tasks:transition:close:department', 'tasks:transition:cancel:department'
      )
  );

DELETE FROM role_permissions
WHERE role_id = (SELECT id FROM roles WHERE name = 'EMPLOYEE')
  AND permission_id NOT IN (
      SELECT id FROM permissions WHERE name IN (
          'tasks:create', 'tasks:read', 'tasks:update:own',
          'tasks:view:assigned', 'tasks:transition:advance:own'
      )
  );
