-- Simplifies RBAC from User <<->> Role (many-to-many via user_roles) down to
-- User -> Role (many-to-one via users.role_id). Role -> Permission stays many-to-many
-- via role_permissions. See com.ratana.task_management_api.model.User/Role.

-- 1. Add the new column nullable first so existing rows can be backfilled.
ALTER TABLE users ADD COLUMN IF NOT EXISTS role_id UUID;

-- 2. Backfill from user_roles, if that table exists. A user who ended up with more
--    than one role keeps the highest-privilege one (ADMIN > MANAGER > EMPLOYEE >
--    anything else, alphabetically as a last resort).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'user_roles') THEN
        UPDATE users u
        SET role_id = ranked.role_id
        FROM (
            SELECT DISTINCT ON (ur.user_id) ur.user_id, ur.role_id
            FROM user_roles ur
            JOIN roles r ON r.id = ur.role_id
            ORDER BY ur.user_id,
                     CASE r.name
                         WHEN 'ADMIN' THEN 0
                         WHEN 'MANAGER' THEN 1
                         WHEN 'EMPLOYEE' THEN 2
                         ELSE 3
                     END,
                     r.name
        ) ranked
        WHERE u.id = ranked.user_id;
    END IF;
END $$;

-- 3. Anyone still without a role (never had a user_roles row) falls back to the
--    least-privileged baseline role, so the NOT NULL constraint below always holds.
UPDATE users
SET role_id = (SELECT id FROM roles WHERE name = 'EMPLOYEE')
WHERE role_id IS NULL
  AND EXISTS (SELECT 1 FROM roles WHERE name = 'EMPLOYEE');

-- 4. Enforce the new invariant: exactly one role per user.
ALTER TABLE users ALTER COLUMN role_id SET NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (id);

CREATE INDEX IF NOT EXISTS idx_users_role_id ON users (role_id);

-- 5. The many-to-many join table is no longer used anywhere in the codebase.
DROP TABLE IF EXISTS user_roles;
