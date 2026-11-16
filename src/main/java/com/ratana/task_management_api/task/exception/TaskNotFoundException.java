package com.ratana.task_management_api.task.exception;

import java.util.UUID;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(String message) {
        super(message);
    }
    public TaskNotFoundException(UUID taskId) {

    }
}
