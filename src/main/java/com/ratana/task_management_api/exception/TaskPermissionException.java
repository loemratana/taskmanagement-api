package com.ratana.task_management_api.exception;

public class TaskPermissionException extends RuntimeException {
    public TaskPermissionException(String message) {
        super(message);
    }
}
