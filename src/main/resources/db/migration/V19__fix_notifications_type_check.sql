-- Hibernate's ddl-auto (update) generated notifications_type_check from
-- NotificationType's constants at the time the table was first created. TASK_REMINDER
-- (and possibly other values) was added to the enum afterward; ddl-auto=update never
-- widens an existing check constraint, so any environment whose table predates those
-- values rejects them, e.g. a ConstraintViolationException when the reminder scheduler
-- inserts a TASK_REMINDER notification.
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN (
        'TASK_ASSIGNED',
        'TASK_UPDATED',
        'TASK_COMPLETED',
        'TASK_OVERDUE',
        'TASK_COMMENTED',
        'TASK_REMINDER',
        'APPROVAL_REQUEST',
        'APPROVAL_APPROVED',
        'APPROVAL_REJECTED',
        'USER_MENTIONED',
        'SYSTEM_ANNOUNCEMENT',
        'SYSTEM_MAINTENANCE'
    ));
