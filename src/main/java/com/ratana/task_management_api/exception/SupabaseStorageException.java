package com.ratana.task_management_api.exception;

public class SupabaseStorageException extends RuntimeException {
    public SupabaseStorageException(String message) {
        super(message);
    }
    public SupabaseStorageException(String message, Exception e) {
        super(message);
    }
}
