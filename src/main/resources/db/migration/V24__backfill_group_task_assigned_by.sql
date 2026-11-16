-- Group task parents were created without assigned_by_id, so they never appeared in the creator's
-- "tasks I assigned" list (GET /tasks?assignedById=...). The creator is the one who assigned them.
UPDATE tasks
SET assigned_by_id = creator_id
WHERE is_group_task = TRUE
  AND assigned_by_id IS NULL;
