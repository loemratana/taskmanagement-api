package com.ratana.task_management_api.auth.service;

import com.ratana.task_management_api.auth.dto.response.DeviceMetadata;
import com.ratana.task_management_api.auth.dto.response.RefreshTokenResponse;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.auth.exception.TokenRefreshException;
import com.ratana.task_management_api.auth.entity.RefreshToken;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.auth.repository.RefreshTokenRepository;
import com.ratana.task_management_api.infrastructure.security.JwtTokenProvider;
import com.ratana.task_management_api.auth.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Service
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenHasher tokenHasher;


    @Override
    public String createRefreshToken(User user, DeviceMetadata deviceMetadata) {

        String rawToken = jwtTokenProvider.generateRefreshToken(user.getEmail());
        String hashed = tokenHasher.hash(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashed)
                .deviceName(deviceMetadata.getDeviceName())
                .deviceType(deviceMetadata.getDeviceType())
                .ipAddress(deviceMetadata.getIpAddress())
                .userAgent(deviceMetadata.getUserAgent())
                .expiresAt(Instant.now().plusMillis(jwtTokenProvider.getRefreshTokenExpirationMs()))
                .build();

        refreshTokenRepository.save(refreshToken);
        log.info("Refresh token issued for user {} on device {}", user.getEmail(), deviceMetadata.getDeviceName());

        return rawToken;
    }

    @Override
    @Transactional
    public RefreshTokenResponse verifyAndGet(String rawToken) {
        String hashed = tokenHasher.hash(rawToken);

        // Locked so a concurrent refresh with the same token waits, then sees it revoked.
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashForUpdate(hashed)
                .orElseThrow(() -> {
                    log.warn("Refresh token rejected: token not recognized");
                    return new TokenRefreshException("Refresh token not recognized. Please log in again.");
                });

        if (!refreshToken.isValid()) {
            String reason = refreshToken.isRevoked() ? "revoked" : "expired";
            log.warn("Refresh token {} of user {} rejected: {}", refreshToken.getId(), refreshToken.getUser().getId(), reason);
            throw new TokenRefreshException("Refresh token is " + reason + ". Please log in again.");
        }

        return toDto(refreshToken);
    }

    @Override
    @Transactional
    public void revokeToken(String rawToken) {
        String hashed = tokenHasher.hash(rawToken);
        refreshTokenRepository.findByTokenHash(hashed)
                .ifPresentOrElse(token -> {
                    token.revoke();
                    // Explicit save: callers outside a transaction would otherwise lose the change.
                    refreshTokenRepository.save(token);
                    log.info("Refresh token {} revoked for user {}", token.getId(), token.getUser().getId());
                }, () -> log.debug("Revoke requested for an unknown refresh token"));
    }

    @Override
    @Transactional
    public void revokeTokenForUser(String rawToken, UUID userId) {
        String hashed = tokenHasher.hash(rawToken);
        refreshTokenRepository.findByTokenHash(hashed)
                .filter(token -> token.getUser().getId().equals(userId))
                .ifPresentOrElse(token -> {
                    if (!token.isRevoked()) {
                        token.revoke();
                        refreshTokenRepository.save(token);
                    }
                    log.info("Refresh token {} revoked by its owner {}", token.getId(), userId);
                }, () -> log.debug("Logout for user {} with an unknown or foreign refresh token", userId));
    }

    @Override
    @Transactional
    public void revokeSessionForUser(UUID sessionId, UUID userId) {
        // Same 404 for "missing" and "someone else's", so session ids can't be probed.
        RefreshToken token = refreshTokenRepository.findById(sessionId)
                .filter(t -> t.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        if (!token.isRevoked()) {
            token.revoke();
            refreshTokenRepository.save(token);
        }
        log.info("Session {} revoked by user {}", sessionId, userId);
    }

    @Override
    public void revokeAllForUser(UUID userId) {

        refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
        log.info("All refresh tokens revoked for user {}", userId);

    }

    @Override
    @Transactional(readOnly = true)
    public List<RefreshTokenResponse> listActiveSessions(UUID userId) {
        log.debug("Listing active sessions for user {}", userId);
        return refreshTokenRepository.findAllByUser_IdAndRevokedAtIsNull(userId)
                .stream()
                .filter(RefreshToken::isValid)
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public void deleteExpiredTokens() {

        int deleted = refreshTokenRepository.deleteAllExpired(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired refresh tokens", deleted);
        } else {
            log.debug("No expired refresh tokens to clean up");
        }


    }

    private RefreshTokenResponse toDto(RefreshToken entity) {
        return RefreshTokenResponse.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .userEmail(entity.getUser().getEmail())
                .deviceName(entity.getDeviceName())
                .deviceType(entity.getDeviceType())
                .ipAddress(entity.getIpAddress())
                .expiresAt(entity.getExpiresAt())
                .createdAt(entity.getCreatedAt())
                .valid(entity.isValid())
                .build();
    }
}
