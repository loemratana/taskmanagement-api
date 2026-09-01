package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.dto.request.RefreshTokenRequest;
import com.ratana.task_management_api.dto.response.DeviceMetadata;
import com.ratana.task_management_api.dto.response.RefreshTokenResponse;
import com.ratana.task_management_api.exception.TokenRefreshException;
import com.ratana.task_management_api.model.RefreshToken;
import com.ratana.task_management_api.model.User;
import com.ratana.task_management_api.repository.RefreshTokenRepository;
import com.ratana.task_management_api.security.JwtTokenProvider;
import com.ratana.task_management_api.service.RefreshTokenService;
import com.ratana.task_management_api.utility.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenServiceImpL implements RefreshTokenService {

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

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashed)
                .orElseThrow(() -> new TokenRefreshException("Refresh token not recognized. Please log in again."));

        if (!refreshToken.isValid()) {
            String reason = refreshToken.isRevoked() ? "revoked" : "expired";
            throw new TokenRefreshException("Refresh token is " + reason + ". Please log in again.");
        }

        return toDto(refreshToken);
    }

    @Override
    public void revokeToken(String rawToken) {
        String hashed = tokenHasher.hash(rawToken);
        refreshTokenRepository.findByTokenHash(hashed)
                .ifPresent(RefreshToken::revoke);
    }

    @Override
    public void revokeAllForUser(UUID userId) {

        refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
        log.info("All refresh tokens revoked for user {}", userId);

    }

    @Override
    @Transactional(readOnly = true)
    public List<RefreshTokenResponse> listActiveSessions(UUID userId) {
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
