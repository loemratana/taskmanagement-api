-- Late submissions: whether the work was submitted after the task's due date (snapshot taken at
-- submit time, so a later due-date change does not rewrite history) and the assignee's reason.
ALTER TABLE task_submissions ADD COLUMN IF NOT EXISTS is_late BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE task_submissions ADD COLUMN IF NOT EXISTS late_reason TEXT;
