-- Indexes for the columns the app filters and joins on most. Before this, most of these lookups
-- (task lists, dashboards, the notification inbox, the retry job, role loading on every request)
-- read the whole table. Each index below names the query it serves.

-- ---------------------------------------------------------------------------------------------
-- tasks: visibility scopes (TaskSpecifications.visibleTo), filters, dashboards, KPI queries.
-- ---------------------------------------------------------------------------------------------
-- "My tasks", team scope, performance and reminder queries filter by assignee.
CREATE INDEX IF NOT EXISTS idx_tasks_assignee ON tasks (assignee_id);
-- Own/team scope ("tasks I created") and the dashboard managerId filter.
CREATE INDEX IF NOT EXISTS idx_tasks_creator ON tasks (creator_id);
-- Manager KPI and dashboard tiles (TaskRepository: t.assignedBy.id = :assignedById).
CREATE INDEX IF NOT EXISTS idx_tasks_assigned_by ON tasks (assigned_by_id);
-- Department scope and the departmentId filter.
CREATE INDEX IF NOT EXISTS idx_tasks_department ON tasks (department_id);
-- Subtasks / group-task members of a parent. Most tasks have no parent, so only index those that do.
CREATE INDEX IF NOT EXISTS idx_tasks_parent ON tasks (parent_task_id) WHERE parent_task_id IS NOT NULL;
-- Overdue checks (due_date < today) and the daily overdue notifier (due_date = yesterday).
CREATE INDEX IF NOT EXISTS idx_tasks_due_date ON tasks (due_date) WHERE due_date IS NOT NULL;
-- Dashboard date range (createdBetween) and newest-first lists.
CREATE INDEX IF NOT EXISTS idx_tasks_created_at ON tasks (created_at);

-- ---------------------------------------------------------------------------------------------
-- Task child tables. Postgres does not index foreign keys automatically.
-- ---------------------------------------------------------------------------------------------
-- Collaborator lookups by user. (task_id is already covered by the UNIQUE (task_id, user_id).)
CREATE INDEX IF NOT EXISTS idx_task_assignments_user ON task_assignments (user_id);
CREATE INDEX IF NOT EXISTS idx_task_comments_task ON task_comments (task_id);
CREATE INDEX IF NOT EXISTS idx_task_logs_task ON task_logs (task_id);
CREATE INDEX IF NOT EXISTS idx_attachments_task ON attachments (task_id);

-- ---------------------------------------------------------------------------------------------
-- Notifications.
-- ---------------------------------------------------------------------------------------------
-- The inbox: findByRecipientIdOrderByCreatedAtDesc / findByRecipientId with paging.
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created
    ON notifications (recipient_id, created_at DESC);
-- Unread badge and "mark all as read": only unread rows, so the index stays small.
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_unread
    ON notifications (recipient_id) WHERE is_read = false;

-- RetryScheduler.findRetryCandidates: status = ? AND last_attempt_at <= ? ORDER BY last_attempt_at.
CREATE INDEX IF NOT EXISTS idx_deliveries_status_last_attempt
    ON notification_deliveries (status, last_attempt_at);
-- The composite index above starts with status, so it serves every status-only lookup too.
DROP INDEX IF EXISTS idx_deliveries_status;

-- ---------------------------------------------------------------------------------------------
-- Users, roles and sessions. Roles/permissions are loaded on every authenticated request.
-- ---------------------------------------------------------------------------------------------
-- PK is (role_id, user_id), which can't serve "roles of this user".
CREATE INDEX IF NOT EXISTS idx_user_roles_user ON user_roles (user_id);
-- PK is (permission_id, role_id), which can't serve "permissions of this role".
CREATE INDEX IF NOT EXISTS idx_role_permissions_role ON role_permissions (role_id);
-- Team scope (users managed by X) and department member lists.
CREATE INDEX IF NOT EXISTS idx_users_manager ON users (manager_id);
CREATE INDEX IF NOT EXISTS idx_users_department ON users (department_id);
-- Logout-all / password change revoke every token of a user.
CREATE INDEX IF NOT EXISTS idx_refresh_token_user ON refresh_token (user_id);
-- Push and channel resolution per recipient on every notification sent.
CREATE INDEX IF NOT EXISTS idx_user_devices_user ON user_devices (user_id);
CREATE INDEX IF NOT EXISTS idx_user_notification_channels_user ON user_notification_channels (user_id);
