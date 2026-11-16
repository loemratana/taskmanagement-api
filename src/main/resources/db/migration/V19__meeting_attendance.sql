-- Meeting attendance: QR check-in by attendees, plus the organizer's checklist.
-- A row exists once someone checked in or was marked; no row = not yet known.
-- After a meeting ends, a job writes ABSENT for everyone still without a row.

CREATE TABLE booking_attendance (
    id             uuid PRIMARY KEY,
    booking_id     uuid        NOT NULL REFERENCES meeting_bookings (id) ON DELETE CASCADE,
    user_id        uuid        NOT NULL REFERENCES users (id),
    status         varchar(20) NOT NULL,
    method         varchar(20) NOT NULL,
    checked_in_at  timestamptz,
    marked_by_id   uuid REFERENCES users (id),
    note           varchar(300),
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,
    CONSTRAINT uq_booking_attendance UNIQUE (booking_id, user_id),
    CONSTRAINT booking_attendance_status_check CHECK (status IN ('PRESENT', 'LATE', 'ABSENT', 'EXCUSED')),
    CONSTRAINT booking_attendance_method_check CHECK (method IN ('QR', 'ORGANIZER', 'AUTO'))
);
CREATE INDEX idx_booking_attendance_user ON booking_attendance (user_id);

-- Set once the "absent after the meeting" job has run for a booking.
ALTER TABLE meeting_bookings ADD COLUMN attendance_finalized_at timestamptz;

-- Lets a dedicated room-display account (e.g. a tablet at the door) show a room's check-in QR.
INSERT INTO permissions (id, name, module, action, is_active, description, created_at, updated_at)
VALUES (gen_random_uuid(), 'rooms:display', 'ROOM', 'display', true,
        'Show the check-in QR code of the meeting currently in a room', now(), now())
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'rooms:display'
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
