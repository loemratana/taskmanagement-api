package com.ratana.task_management_api.booking.exception;

/** The booking can no longer be changed, e.g. it already started or was cancelled (HTTP 409). */
public class BookingStateException extends RuntimeException {
    public BookingStateException(String message) {
        super(message);
    }
}
