package com.ratana.task_management_api.infrastructure.storage;

public class InvalidDirectoryException extends RuntimeException {
    public InvalidDirectoryException(String message) {
        super(message);
    }
}
