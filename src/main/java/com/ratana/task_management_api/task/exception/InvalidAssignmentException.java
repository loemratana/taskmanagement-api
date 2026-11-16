package com.ratana.task_management_api.task.exception;

public class InvalidAssignmentException extends RuntimeException {
    public InvalidAssignmentException(String message) {
        super(message);
    }
}
