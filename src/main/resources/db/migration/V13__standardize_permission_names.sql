-- Permission names had drifted onto two conventions: the tasks:* rows always used a
-- plural, module:action[:scope] shape, but the roles/permissions/positions/users admin
-- endpoints were seeded as singular ("role:read", "permission:read", "position:read") and
-- users:view used "view" where every other resource uses "read". Java code now references
-- these strings only via com.ratana.task_management_api.security.permission.Permissions, so
-- this rename is safe to do in one place. Plain UPDATEs are idempotent: after the first run
-- the old name no longer exists, so re-running this migration is a no-op.

UPDATE permissions SET name = 'roles:read' WHERE name = 'role:read';
UPDATE permissions SET name = 'roles:create' WHERE name = 'role:create';
UPDATE permissions SET name = 'roles:update' WHERE name = 'role:update';
UPDATE permissions SET name = 'roles:delete' WHERE name = 'role:delete';
UPDATE permissions SET name = 'roles:assign_permissions' WHERE name = 'role:assign_permissions';

UPDATE permissions SET name = 'permissions:read' WHERE name = 'permission:read';
UPDATE permissions SET name = 'permissions:create' WHERE name = 'permission:create';
UPDATE permissions SET name = 'permissions:update' WHERE name = 'permission:update';
UPDATE permissions SET name = 'permissions:delete' WHERE name = 'permission:delete';

UPDATE permissions SET name = 'positions:read' WHERE name = 'position:read';

UPDATE permissions SET name = 'users:read' WHERE name = 'users:view';
