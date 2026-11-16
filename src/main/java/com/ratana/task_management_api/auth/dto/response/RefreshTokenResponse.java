package com.ratana.task_management_api.auth.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record RefreshTokenResponse(
        UUID id,
        UUID userId,
        String userEmail,
        String deviceName,
        String deviceType,
        String ipAddress,
        Instant expiresAt,
        Instant createdAt,
        boolean valid
) {
}
