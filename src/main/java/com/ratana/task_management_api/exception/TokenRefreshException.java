package com.ratana.task_management_api.exception;

public class TokenRefreshException extends RuntimeException {
    public TokenRefreshException(String message) {
        super(message);
    }

    public TokenRefreshException(String token , String message) {
        super(message);
    }

}
