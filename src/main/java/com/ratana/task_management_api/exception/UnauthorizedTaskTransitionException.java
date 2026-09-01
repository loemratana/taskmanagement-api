package com.ratana.task_management_api.exception;

public class UnauthorizedTaskTransitionException extends RuntimeException {
    public UnauthorizedTaskTransitionException(String message) {
        super(message);
    }
}
