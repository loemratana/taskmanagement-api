-- Meeting rooms (with image gallery and capacity) and room bookings with audience targeting.

-- Needed so the exclusion constraint can combine "room_id =" with "time range &&".
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------- Rooms

CREATE TABLE rooms (
    id           uuid PRIMARY KEY,
    name         varchar(100) NOT NULL,
    code         varchar(20),
    location     varchar(200),
    description  text,
    capacity     integer      NOT NULL,
    status       varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    version      bigint       NOT NULL DEFAULT 0,
    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL,
    CONSTRAINT rooms_capacity_check CHECK (capacity BETWEEN 1 AND 500),
    CONSTRAINT rooms_status_check CHECK (status IN ('ACTIVE', 'MAINTENANCE', 'INACTIVE'))
);
CREATE UNIQUE INDEX uq_rooms_name ON rooms (lower(name));
CREATE UNIQUE INDEX uq_rooms_code ON rooms (lower(code)) WHERE code IS NOT NULL;

CREATE TABLE room_facilities (
    room_id   uuid        NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    facility  varchar(30) NOT NULL,
    PRIMARY KEY (room_id, facility)
);

CREATE TABLE room_images (
    id            uuid PRIMARY KEY,
    room_id       uuid         NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    storage_key   text         NOT NULL,
    content_type  varchar(100) NOT NULL,
    size_bytes    bigint       NOT NULL,
    sort_order    integer      NOT NULL DEFAULT 0,
    is_cover      boolean      NOT NULL DEFAULT false,
    created_at    timestamptz  NOT NULL
);
CREATE INDEX idx_room_images_room ON room_images (room_id, sort_order);
-- At most one cover image per room.
CREATE UNIQUE INDEX uq_room_images_cover ON room_images (room_id) WHERE is_cover;

-- ---------------------------------------------------------------- Bookings

CREATE TABLE meeting_bookings (
    id              uuid PRIMARY KEY,
    room_id         uuid         NOT NULL REFERENCES rooms (id),
    organizer_id    uuid         NOT NULL REFERENCES users (id),
    title           varchar(200) NOT NULL,
    description     text,
    start_at        timestamptz  NOT NULL,
    end_at          timestamptz  NOT NULL,
    status          varchar(20)  NOT NULL DEFAULT 'CONFIRMED',
    attendee_count  integer      NOT NULL DEFAULT 0,
    cancelled_at    timestamptz,
    cancelled_by_id uuid REFERENCES users (id),
    cancel_reason   varchar(500),
    version         bigint       NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT meeting_bookings_time_check CHECK (end_at > start_at),
    CONSTRAINT meeting_bookings_status_check CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    -- A room can never hold two live bookings at overlapping times. '[)' lets 15:00-16:00
    -- and 16:00-17:00 sit back to back. Cancelled bookings free the slot.
    CONSTRAINT no_room_overlap EXCLUDE USING gist (
        room_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    ) WHERE (status = 'CONFIRMED')
);
CREATE INDEX idx_meeting_bookings_organizer ON meeting_bookings (organizer_id, start_at);
CREATE INDEX idx_meeting_bookings_start ON meeting_bookings (start_at);

-- What the organizer selected.
CREATE TABLE booking_target_departments (
    booking_id    uuid NOT NULL REFERENCES meeting_bookings (id) ON DELETE CASCADE,
    department_id uuid NOT NULL REFERENCES departments (id),
    PRIMARY KEY (booking_id, department_id)
);

CREATE TABLE booking_target_positions (
    booking_id  uuid NOT NULL REFERENCES meeting_bookings (id) ON DELETE CASCADE,
    position_id uuid NOT NULL REFERENCES positions (id),
    PRIMARY KEY (booking_id, position_id)
);

CREATE TABLE booking_target_users (
    booking_id uuid NOT NULL REFERENCES meeting_bookings (id) ON DELETE CASCADE,
    user_id    uuid NOT NULL REFERENCES users (id),
    PRIMARY KEY (booking_id, user_id)
);

-- Who was actually invited: targets resolved to active users when the booking was saved
-- (organizer included), so later department/position moves do not rewrite past meetings.
CREATE TABLE booking_attendees (
    booking_id uuid NOT NULL REFERENCES meeting_bookings (id) ON DELETE CASCADE,
    user_id    uuid NOT NULL REFERENCES users (id),
    PRIMARY KEY (booking_id, user_id)
);
CREATE INDEX idx_booking_attendees_user ON booking_attendees (user_id);

-- ABAC: extra departments an admin lets a user invite (used with bookings:invite:assigned).
CREATE TABLE user_booking_scopes (
    id            uuid PRIMARY KEY,
    user_id       uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    department_id uuid        NOT NULL REFERENCES departments (id) ON DELETE CASCADE,
    granted_by_id uuid REFERENCES users (id),
    created_at    timestamptz NOT NULL,
    CONSTRAINT uq_user_booking_scope UNIQUE (user_id, department_id)
);

-- ---------------------------------------------------------------- Notification types

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'TASK_ASSIGNED', 'TASK_UPDATED', 'TASK_COMPLETED', 'TASK_OVERDUE', 'TASK_COMMENTED', 'TASK_REMINDER',
    'APPROVAL_REQUEST', 'APPROVAL_APPROVED', 'APPROVAL_REJECTED',
    'USER_MENTIONED',
    'SYSTEM_ANNOUNCEMENT', 'SYSTEM_MAINTENANCE',
    'MEETING_INVITED', 'MEETING_UPDATED', 'MEETING_CANCELLED'
));

-- ---------------------------------------------------------------- Permissions

INSERT INTO permissions (id, name, module, action, is_active, description, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'rooms:view', 'ROOM', 'view', true,
        'View meeting rooms, their schedule and availability', now(), now()),
    (gen_random_uuid(), 'rooms:manage', 'ROOM', 'manage', true,
        'Create and edit meeting rooms and their images', now(), now()),
    (gen_random_uuid(), 'bookings:create', 'BOOKING', 'create', true,
        'Book meeting rooms', now(), now()),
    (gen_random_uuid(), 'bookings:view:all', 'BOOKING', 'view-all', true,
        'View every booking, not only meetings you attend', now(), now()),
    (gen_random_uuid(), 'bookings:manage:any', 'BOOKING', 'manage-any', true,
        'Edit or cancel bookings made by anyone', now(), now()),
    (gen_random_uuid(), 'bookings:invite:team', 'BOOKING', 'invite-team', true,
        'Invite your direct reports', now(), now()),
    (gen_random_uuid(), 'bookings:invite:department', 'BOOKING', 'invite-department', true,
        'Invite your own department, its positions and its members', now(), now()),
    (gen_random_uuid(), 'bookings:invite:assigned', 'BOOKING', 'invite-assigned', true,
        'Invite departments an admin assigned to you (plus your own)', now(), now()),
    (gen_random_uuid(), 'bookings:invite:all', 'BOOKING', 'invite-all', true,
        'Invite any department, position or user', now(), now()),
    (gen_random_uuid(), 'bookings:scopes:manage', 'BOOKING', 'scopes-manage', true,
        'Assign which departments a user may invite', now(), now())
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name LIKE 'rooms:%' OR p.name LIKE 'bookings:%'
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name IN ('rooms:view', 'bookings:create', 'bookings:invite:department')
WHERE r.name = 'MANAGER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'rooms:view'
WHERE r.name = 'EMPLOYEE'
ON CONFLICT DO NOTHING;
