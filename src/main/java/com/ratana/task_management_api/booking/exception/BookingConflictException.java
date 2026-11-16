package com.ratana.task_management_api.booking.exception;

/** A room booking clashes with an existing confirmed booking (HTTP 409). */
public class BookingConflictException extends RuntimeException {
    public BookingConflictException(String message) {
        super(message);
    }
}
