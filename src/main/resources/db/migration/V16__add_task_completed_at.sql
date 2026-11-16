-- When a task last became DONE or APPROVED; NULL while it isn't completed.
-- Replaces using updated_at as a stand-in, which moved whenever a finished task was edited.
ALTER TABLE tasks ADD COLUMN completed_at timestamp(6) with time zone;

-- Backfill existing completed tasks: latest approved submission review time, else the last update.
UPDATE tasks t
SET completed_at = COALESCE(
        (SELECT MAX(s.reviewed_at)
         FROM task_submissions s
         WHERE s.task_id = t.id AND s.status = 'APPROVED'),
        t.updated_at)
WHERE t.status IN ('DONE', 'APPROVED');

CREATE INDEX idx_tasks_completed_at ON tasks(completed_at) WHERE completed_at IS NOT NULL;
