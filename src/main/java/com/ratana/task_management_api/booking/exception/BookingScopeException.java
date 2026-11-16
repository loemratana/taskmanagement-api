package com.ratana.task_management_api.booking.exception;

/** The organizer may not invite one of the selected targets (HTTP 403). */
public class BookingScopeException extends RuntimeException {
    public BookingScopeException(String message) {
        super(message);
    }
}
