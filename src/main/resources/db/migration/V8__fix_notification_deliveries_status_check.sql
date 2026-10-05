-- Hibernate's ddl-auto (update) generated notification_deliveries_status_check from
-- DeliveryStatus's constants at the time the table was first created. DEAD_LETTER was
-- added to the enum afterward; ddl-auto=update never widens an existing check
-- constraint, so any environment whose table predates DEAD_LETTER rejects it —
-- e.g. HHH000247 / SQLState 23514 when the retry scheduler marks a delivery dead.
ALTER TABLE notification_deliveries DROP CONSTRAINT IF EXISTS notification_deliveries_status_check;

ALTER TABLE notification_deliveries
    ADD CONSTRAINT notification_deliveries_status_check
    CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'DEAD_LETTER'));
