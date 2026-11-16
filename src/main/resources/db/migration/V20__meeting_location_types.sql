-- Meetings can now happen outside our rooms: at a pinned map location (EXTERNAL) or on a
-- video call (ONLINE). Existing bookings are all ROOM, which is the column default.
--
-- no_room_overlap needs no change: rows with a NULL room_id never compare equal, so
-- external and online meetings never block each other or any room.

ALTER TABLE meeting_bookings
    ALTER COLUMN room_id DROP NOT NULL,
    ADD COLUMN location_type    varchar(20)   NOT NULL DEFAULT 'ROOM',
    ADD COLUMN location_name    varchar(200),
    ADD COLUMN location_address varchar(500),
    ADD COLUMN latitude         numeric(9, 6),
    ADD COLUMN longitude        numeric(9, 6),
    ADD COLUMN google_place_id  varchar(255),
    ADD COLUMN meeting_url      varchar(1000),
    ADD COLUMN location_note    varchar(500);

ALTER TABLE meeting_bookings
    ADD CONSTRAINT meeting_bookings_location_type_check
        CHECK (location_type IN ('ROOM', 'EXTERNAL', 'ONLINE')),
    ADD CONSTRAINT meeting_bookings_coordinates_check
        CHECK ((latitude IS NULL AND longitude IS NULL)
            OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)),
    -- What each type must (and must not) carry. location_note is allowed for every type.
    ADD CONSTRAINT meeting_bookings_location_check CHECK (
        (location_type = 'ROOM'
            AND room_id IS NOT NULL
            AND location_name IS NULL AND location_address IS NULL
            AND latitude IS NULL AND google_place_id IS NULL AND meeting_url IS NULL)
        OR (location_type = 'EXTERNAL'
            AND room_id IS NULL
            AND location_name IS NOT NULL AND latitude IS NOT NULL
            AND meeting_url IS NULL)
        OR (location_type = 'ONLINE'
            AND room_id IS NULL
            AND meeting_url IS NOT NULL
            AND location_address IS NULL AND latitude IS NULL AND google_place_id IS NULL)
    );
