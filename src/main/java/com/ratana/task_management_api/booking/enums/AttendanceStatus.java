package com.ratana.task_management_api.booking.enums;

/** Stored attendance of one invitee. "Not known yet" is the absence of a row. */
public enum AttendanceStatus {
    PRESENT,
    LATE,
    ABSENT,
    /** Organizer accepted a reason for missing it; not counted as absent. */
    EXCUSED
}
