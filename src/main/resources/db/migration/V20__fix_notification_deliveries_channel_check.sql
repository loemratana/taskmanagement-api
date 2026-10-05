-- Hibernate's ddl-auto (update) generated notification_deliveries_channel_check from
-- NotificationChannel's constants at the time the table was first created. TELEGRAM_GROUP
-- was added to the enum afterward; ddl-auto=update never widens an existing check
-- constraint, so any environment whose table predates it rejects the insert —
-- silently rolling back the whole notification (a ConstraintViolationException /
-- SQLState 23514) whenever a department Telegram group broadcast is attempted.
ALTER TABLE notification_deliveries DROP CONSTRAINT IF EXISTS notification_deliveries_channel_check;

ALTER TABLE notification_deliveries
    ADD CONSTRAINT notification_deliveries_channel_check
    CHECK (channel IN ('IN_APP', 'EMAIL', 'TELEGRAM', 'TELEGRAM_GROUP', 'PUSH'));
