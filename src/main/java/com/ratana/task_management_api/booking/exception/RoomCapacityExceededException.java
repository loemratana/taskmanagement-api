package com.ratana.task_management_api.booking.exception;

/** More attendees than the room holds (HTTP 422). */
public class RoomCapacityExceededException extends RuntimeException {
    public RoomCapacityExceededException(String message) {
        super(message);
    }
}
