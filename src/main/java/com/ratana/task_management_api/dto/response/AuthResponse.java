package com.ratana.task_management_api.dto.response;


import lombok.Builder;
import lombok.Data;

public record AuthResponse (
        String accessToken,
        String refreshToken,
        String tokenType
)
{
    public AuthResponse(String accessToken, String refreshToken) {
        this(accessToken, refreshToken, "Bearer");
    }
}