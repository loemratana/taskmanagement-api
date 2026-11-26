-- ShedLock: one row per scheduled job. When several API instances run, only the instance holding a
-- job's row lock runs it, so reminders, overdue alerts, awards and retries are not sent twice.
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
