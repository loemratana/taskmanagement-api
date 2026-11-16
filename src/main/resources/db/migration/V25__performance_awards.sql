-- Top-performer announcements: a frozen copy of a department's ranking, published by the scheduler
-- (monthly on the 1st, yearly on 2 January, 09:00) so the popup always shows what was announced,
-- even if late reviews change the live leaderboard afterwards.

CREATE TABLE performance_awards (
    id               uuid PRIMARY KEY,
    department_id    uuid         NOT NULL REFERENCES departments (id) ON DELETE CASCADE,
    department_name  varchar(255) NOT NULL,
    period           varchar(20)  NOT NULL,
    period_year      integer      NOT NULL,
    -- Month (1-12) or quarter (1-4); 0 for a yearly award, so the unique key never holds a NULL.
    period_number    integer      NOT NULL,
    period_from      date         NOT NULL,
    period_to        date         NOT NULL,
    published_at     timestamptz  NOT NULL,
    CONSTRAINT performance_awards_period_check CHECK (period IN ('MONTHLY', 'QUARTERLY', 'YEARLY')),
    -- One award per department and period: a second scheduler run (or a second server) can't publish twice.
    CONSTRAINT uq_performance_awards_period UNIQUE (department_id, period, period_year, period_number)
);

CREATE INDEX idx_performance_awards_department_published ON performance_awards (department_id, published_at DESC);

-- Everyone who was rated, in rank order.
CREATE TABLE performance_award_entries (
    id             uuid PRIMARY KEY,
    award_id       uuid             NOT NULL REFERENCES performance_awards (id) ON DELETE CASCADE,
    award_rank     integer          NOT NULL,
    user_id        uuid             NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    full_name      varchar(255)     NOT NULL,
    avatar_url     varchar(1000),
    position_name  varchar(255),
    score          double precision NOT NULL,
    stars          double precision NOT NULL,
    tasks          integer          NOT NULL,
    CONSTRAINT uq_performance_award_entries_rank UNIQUE (award_id, award_rank)
);

-- Who has closed the popup; an award stays "pending" for a user until it has a row here.
CREATE TABLE performance_award_views (
    id        uuid PRIMARY KEY,
    award_id  uuid        NOT NULL REFERENCES performance_awards (id) ON DELETE CASCADE,
    user_id   uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    seen_at   timestamptz NOT NULL,
    CONSTRAINT uq_performance_award_views UNIQUE (award_id, user_id)
);

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'TASK_ASSIGNED', 'TASK_UPDATED', 'TASK_COMPLETED', 'TASK_OVERDUE', 'TASK_COMMENTED', 'TASK_REMINDER',
    'APPROVAL_REQUEST', 'APPROVAL_APPROVED', 'APPROVAL_REJECTED',
    'USER_MENTIONED',
    'SYSTEM_ANNOUNCEMENT', 'SYSTEM_MAINTENANCE',
    'MEETING_INVITED', 'MEETING_UPDATED', 'MEETING_CANCELLED',
    'PERFORMANCE_AWARD'
));
