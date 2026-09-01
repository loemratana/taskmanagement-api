package com.ratana.task_management_api.service;

import com.ratana.task_management_api.dto.request.RefreshTokenRequest;
import com.ratana.task_management_api.dto.response.DeviceMetadata;
import com.ratana.task_management_api.dto.response.RefreshTokenResponse;
import com.ratana.task_management_api.model.RefreshToken;
import com.ratana.task_management_api.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenService {
    String createRefreshToken(User user, DeviceMetadata deviceMetadata);

    RefreshTokenResponse verifyAndGet(String rawToken);

    void revokeToken(String rawToken);

    void revokeAllForUser(UUID userId);

    List<RefreshTokenResponse> listActiveSessions(UUID userId);

    void deleteExpiredTokens();
}
