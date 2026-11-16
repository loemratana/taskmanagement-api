package com.ratana.task_management_api.auth.service;

import com.ratana.task_management_api.auth.dto.response.DeviceMetadata;
import com.ratana.task_management_api.auth.dto.response.RefreshTokenResponse;
import com.ratana.task_management_api.user.entity.User;

import java.util.List;
import java.util.UUID;

public interface RefreshTokenService {
    String createRefreshToken(User user, DeviceMetadata deviceMetadata);

    RefreshTokenResponse verifyAndGet(String rawToken);

    void revokeToken(String rawToken);

    /** Revokes one refresh token, only if it belongs to {@code userId}; unknown or foreign tokens are ignored. */
    void revokeTokenForUser(String rawToken, UUID userId);

    /** Revokes one session by id; throws ResourceNotFoundException if it isn't one of {@code userId}'s sessions. */
    void revokeSessionForUser(UUID sessionId, UUID userId);

    void revokeAllForUser(UUID userId);

    List<RefreshTokenResponse> listActiveSessions(UUID userId);

    void deleteExpiredTokens();
}
