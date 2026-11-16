package com.ratana.task_management_api.booking.enums;

public enum RoomStatus {
    /** Bookable. */
    ACTIVE,
    /** Temporarily not bookable; existing bookings stay. */
    MAINTENANCE,
    /** Retired; not bookable and hidden from pickers by default. */
    INACTIVE
}
