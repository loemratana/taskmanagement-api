-- Announcements module. IF NOT EXISTS guards: in dev, ddl-auto=update may have
-- created these tables already.

CREATE TABLE IF NOT EXISTS announcements (
    id              uuid PRIMARY KEY,
    version         bigint NOT NULL DEFAULT 0,
    title           varchar(200) NOT NULL,
    summary         varchar(500),
    description     text,
    type            varchar(20) NOT NULL,
    priority        varchar(20) NOT NULL,
    status          varchar(20) NOT NULL,
    publish_mode    varchar(20) NOT NULL,
    publish_at      timestamp(6) with time zone,
    end_at          timestamp(6) with time zone,
    published_at    timestamp(6) with time zone,
    banner_url      text,
    banner_name     varchar(255),
    attachment_url  text,
    attachment_name varchar(255),
    send_in_app     boolean NOT NULL DEFAULT true,
    send_push       boolean NOT NULL DEFAULT false,
    send_email      boolean NOT NULL DEFAULT false,
    pinned          boolean NOT NULL DEFAULT false,
    audience_type   varchar(30) NOT NULL,
    created_by_id   uuid NOT NULL REFERENCES users(id),
    created_at      timestamp(6) with time zone,
    updated_at      timestamp(6) with time zone,
    CONSTRAINT announcements_type_check
        CHECK (type IN ('COMPANY', 'DEPARTMENT', 'HOLIDAY', 'EVENT')),
    CONSTRAINT announcements_priority_check
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT announcements_status_check
        CHECK (status IN ('DRAFT', 'SCHEDULED', 'PUBLISHED', 'EXPIRED', 'ARCHIVED')),
    CONSTRAINT announcements_publish_mode_check
        CHECK (publish_mode IN ('NOW', 'SCHEDULED')),
    CONSTRAINT announcements_audience_type_check
        CHECK (audience_type IN ('EVERYONE', 'DEPARTMENT', 'TEAM', 'SELECTED_EMPLOYEES')),
    CONSTRAINT announcements_end_after_publish
        CHECK (end_at IS NULL OR publish_at IS NULL OR end_at > publish_at)
);
-- Scheduler lookups (scheduled -> published, published -> expired) and list sorting.
CREATE INDEX IF NOT EXISTS idx_announcements_status_publish_at ON announcements(status, publish_at);
CREATE INDEX IF NOT EXISTS idx_announcements_status_end_at ON announcements(status, end_at);
CREATE INDEX IF NOT EXISTS idx_announcements_created_by ON announcements(created_by_id);

CREATE TABLE IF NOT EXISTS announcement_departments (
    announcement_id uuid NOT NULL REFERENCES announcements(id) ON DELETE CASCADE,
    department_id   uuid NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    PRIMARY KEY (announcement_id, department_id)
);
CREATE INDEX IF NOT EXISTS idx_announcement_departments_dept ON announcement_departments(department_id);

CREATE TABLE IF NOT EXISTS announcement_team_managers (
    announcement_id uuid NOT NULL REFERENCES announcements(id) ON DELETE CASCADE,
    manager_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (announcement_id, manager_id)
);
CREATE INDEX IF NOT EXISTS idx_announcement_team_managers_mgr ON announcement_team_managers(manager_id);

CREATE TABLE IF NOT EXISTS announcement_users (
    announcement_id uuid NOT NULL REFERENCES announcements(id) ON DELETE CASCADE,
    user_id         uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (announcement_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_announcement_users_user ON announcement_users(user_id);

CREATE TABLE IF NOT EXISTS announcement_reads (
    id               uuid PRIMARY KEY,
    announcement_id  uuid NOT NULL REFERENCES announcements(id) ON DELETE CASCADE,
    user_id          uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    first_viewed_at  timestamp(6) with time zone NOT NULL,
    last_viewed_at   timestamp(6) with time zone NOT NULL,
    view_count       integer NOT NULL DEFAULT 1,
    read_at          timestamp(6) with time zone,
    created_at       timestamp(6) with time zone,
    updated_at       timestamp(6) with time zone,
    CONSTRAINT uq_announcement_read_user UNIQUE (announcement_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_announcement_reads_user ON announcement_reads(user_id);
