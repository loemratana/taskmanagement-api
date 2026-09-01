-- IF NOT EXISTS guards throughout: in dev, ddl-auto=update can create these tables from
-- the entity classes before this migration runs (see V2 for the same precedent).
CREATE TABLE IF NOT EXISTS task_reminder_settings (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL UNIQUE REFERENCES tasks(id),
    enabled BOOLEAN NOT NULL DEFAULT true,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Phnom_Penh',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS task_reminder_schedules (
    id UUID PRIMARY KEY,
    task_reminder_setting_id UUID NOT NULL REFERENCES task_reminder_settings(id) ON DELETE CASCADE,
    reminder_time TIME NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (task_reminder_setting_id, reminder_time)
);

CREATE INDEX IF NOT EXISTS idx_task_reminder_schedules_setting ON task_reminder_schedules(task_reminder_setting_id);

CREATE TABLE IF NOT EXISTS task_reminder_logs (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES tasks(id),
    task_reminder_schedule_id UUID NOT NULL REFERENCES task_reminder_schedules(id),
    reminder_date DATE NOT NULL,
    sent_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL,
    notification_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (task_id, task_reminder_schedule_id, reminder_date)
);

CREATE INDEX IF NOT EXISTS idx_task_reminder_logs_task_date ON task_reminder_logs(task_id, reminder_date);

DROP TRIGGER IF EXISTS triger_task_reminder_settings_updated_at ON task_reminder_settings;
CREATE TRIGGER triger_task_reminder_settings_updated_at
    BEFORE UPDATE ON task_reminder_settings
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS triger_task_reminder_schedules_updated_at ON task_reminder_schedules;
CREATE TRIGGER triger_task_reminder_schedules_updated_at
    BEFORE UPDATE ON task_reminder_schedules
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS triger_task_reminder_logs_updated_at ON task_reminder_logs;
CREATE TRIGGER triger_task_reminder_logs_updated_at
    BEFORE UPDATE ON task_reminder_logs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
