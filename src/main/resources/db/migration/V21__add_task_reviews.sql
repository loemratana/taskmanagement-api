-- CREATE TABLE IF NOT EXISTS: in dev, ddl-auto=update can create this table from the
-- entity class before this migration runs (see V6 for the same precedent) — but it
-- generates the decision check from ALL TaskStatus values and no rating range check,
-- so those are re-applied unconditionally below regardless of who created the table.
CREATE TABLE IF NOT EXISTS task_reviews (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL UNIQUE REFERENCES tasks(id) ON DELETE CASCADE,
    reviewer_id UUID NOT NULL REFERENCES users(id),
    rating INTEGER NOT NULL,
    comment TEXT NOT NULL,
    decision VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE task_reviews DROP CONSTRAINT IF EXISTS task_reviews_decision_check;
ALTER TABLE task_reviews
    ADD CONSTRAINT task_reviews_decision_check
    CHECK (decision IN ('APPROVED', 'REJECTED'));

ALTER TABLE task_reviews DROP CONSTRAINT IF EXISTS task_reviews_rating_check;
ALTER TABLE task_reviews
    ADD CONSTRAINT task_reviews_rating_check
    CHECK (rating BETWEEN 1 AND 5);

CREATE INDEX IF NOT EXISTS idx_task_reviews_reviewer_id ON task_reviews(reviewer_id);

DROP TRIGGER IF EXISTS trigger_task_reviews_updated_at ON task_reviews;
CREATE TRIGGER trigger_task_reviews_updated_at
    BEFORE UPDATE ON task_reviews
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
