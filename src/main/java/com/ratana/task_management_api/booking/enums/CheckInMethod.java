package com.ratana.task_management_api.booking.enums;

public enum CheckInMethod {
    /** Attendee scanned the meeting QR code. */
    QR,
    /** Organizer set it on the checklist. */
    ORGANIZER,
    /** Set by the system: organizer present when opening check-in, absent after the meeting. */
    AUTO
}
