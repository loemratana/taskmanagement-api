package com.ratana.task_management_api.exception;

public class ProtectedResourceException extends RuntimeException {
    public ProtectedResourceException(String message) {
        super(message);
    }
}
