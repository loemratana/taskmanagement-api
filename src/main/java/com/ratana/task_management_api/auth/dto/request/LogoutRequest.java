package com.ratana.task_management_api.auth.dto.request;

/** Optional body of POST /auth/logout: the refresh token of the session to end. */
public record LogoutRequest(String refreshToken) {
}
