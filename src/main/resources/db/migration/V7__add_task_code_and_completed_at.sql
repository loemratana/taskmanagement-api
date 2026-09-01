-- Human-readable task identifier (e.g. TASK-000042), generated at creation time from
-- a dedicated sequence so codes stay stable and gap-tolerant even if a create fails after
-- the sequence is consumed.
CREATE SEQUENCE IF NOT EXISTS task_code_seq START WITH 1 INCREMENT BY 1;

ALTER TABLE tasks ADD COLUMN IF NOT EXISTS task_code VARCHAR(20);

-- Backfill existing rows in creation order so earlier tasks keep lower numbers.
UPDATE tasks
SET task_code = 'TASK-' || LPAD(nextval('task_code_seq')::text, 6, '0')
WHERE task_code IS NULL;

ALTER TABLE tasks ALTER COLUMN task_code SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_tasks_task_code'
    ) THEN
        ALTER TABLE tasks ADD CONSTRAINT uq_tasks_task_code UNIQUE (task_code);
    END IF;
END $$;

-- When a task reaches DONE, the timestamp is recorded here; cleared again if reopened.
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;
