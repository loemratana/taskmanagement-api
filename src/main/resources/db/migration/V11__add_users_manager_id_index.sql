-- Postgres does not auto-index the referencing side of a foreign key.
-- The recursive org-hierarchy queries (WITH RECURSIVE ... JOIN users child ON child.manager_id = ...)
-- repeatedly look up children by manager_id, so this index is load-bearing for their performance.
CREATE INDEX IF NOT EXISTS idx_users_manager_id ON users (manager_id);
