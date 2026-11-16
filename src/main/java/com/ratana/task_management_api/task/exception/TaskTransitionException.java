package com.ratana.task_management_api.task.exception;

public class TaskTransitionException extends RuntimeException {
    public TaskTransitionException(String message) {
        super(message);
    }
}
