package com.ratana.task_management_api.task.exception;

public class TaskPermissionException extends RuntimeException {
    public TaskPermissionException(String message) {
        super(message);
    }
}
