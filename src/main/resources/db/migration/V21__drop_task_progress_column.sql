-- Task progress is derived from status and subtasks (Task.computeProgress()); the stored column was never written.
ALTER TABLE tasks DROP COLUMN IF EXISTS progress;
