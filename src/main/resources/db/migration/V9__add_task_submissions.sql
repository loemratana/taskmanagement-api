CREATE TABLE task_submissions (
    id              uuid PRIMARY KEY,
    task_id         uuid NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    submitted_by_id uuid NOT NULL REFERENCES users(id),
    round           integer NOT NULL,
    note            text,
    status          varchar(20) NOT NULL,
    reviewed_by_id  uuid REFERENCES users(id),
    review_comment  text,
    reviewed_at     timestamp(6) with time zone,
    submitted_at    timestamp(6) with time zone NOT NULL,
    CONSTRAINT uq_task_submission_round UNIQUE (task_id, round)
);
CREATE INDEX idx_task_submissions_task ON task_submissions(task_id);

CREATE TABLE task_submission_files (
    id            uuid PRIMARY KEY,
    submission_id uuid NOT NULL REFERENCES task_submissions(id) ON DELETE CASCADE,
    kind          varchar(10) NOT NULL,
    storage_key   varchar(512) NOT NULL,
    original_name varchar(255) NOT NULL,
    content_type  varchar(100) NOT NULL,
    file_size     bigint NOT NULL,
    created_at    timestamp(6) with time zone NOT NULL
);
CREATE INDEX idx_submission_files_submission ON task_submission_files(submission_id);
