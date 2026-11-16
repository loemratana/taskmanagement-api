package com.ratana.task_management_api.booking.enums;

/** Where a meeting takes place. */
public enum MeetingLocationType {
    /** One of our bookable rooms: capacity and double-booking are enforced. */
    ROOM,
    /** A place outside our rooms, pinned on a map (client office, café, site visit). */
    EXTERNAL,
    /** A video call (Zoom, Google Meet, Teams, ...). */
    ONLINE
}
