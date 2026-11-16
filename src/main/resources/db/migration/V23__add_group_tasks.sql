-- Group tasks: a task given to several people (or a whole department) is a parent with one
-- member subtask per person. The parent has no assignee; its status follows its members and it
-- closes automatically once every member's subtask is approved.
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS is_group_task BOOLEAN NOT NULL DEFAULT FALSE;
