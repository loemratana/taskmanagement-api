package com.ratana.task_management_api.auth.service;

import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.auth.exception.TokenRefreshException;
import com.ratana.task_management_api.auth.entity.RefreshToken;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.auth.repository.RefreshTokenRepository;
import com.ratana.task_management_api.infrastructure.security.JwtTokenProvider;
import com.ratana.task_management_api.auth.security.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** DEF-02: rotation must actually revoke the presented refresh token (TC-AUTH-06/07). */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private TokenHasher tokenHasher;
    @InjectMocks private RefreshTokenServiceImpl service;

    private RefreshToken token;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(UUID.randomUUID());
        token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash("h")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        // Lenient: the session-id tests never hash a raw token.
        lenient().when(tokenHasher.hash("raw")).thenReturn("h");
    }

    @Test
    @DisplayName("logout revokes only the presented token of its owner")
    void revokeTokenForUser_revokesOwnToken() {
        when(refreshTokenRepository.findByTokenHash("h")).thenReturn(Optional.of(token));

        service.revokeTokenForUser("raw", token.getUser().getId());

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
        verify(refreshTokenRepository, never()).revokeAllByUserId(any(), any());
    }

    @Test
    @DisplayName("logout ignores a refresh token that belongs to someone else")
    void revokeTokenForUser_ignoresForeignToken() {
        when(refreshTokenRepository.findByTokenHash("h")).thenReturn(Optional.of(token));

        service.revokeTokenForUser("raw", UUID.randomUUID());

        assertThat(token.isRevoked()).isFalse();
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("revoking a session by id revokes the caller's own session")
    void revokeSessionForUser_revokesOwnSession() {
        when(refreshTokenRepository.findById(token.getId())).thenReturn(Optional.of(token));

        service.revokeSessionForUser(token.getId(), token.getUser().getId());

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    @DisplayName("revoking another user's session is a 404, not a silent success")
    void revokeSessionForUser_rejectsForeignSession() {
        when(refreshTokenRepository.findById(token.getId())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.revokeSessionForUser(token.getId(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(token.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("revokeToken persists the revocation")
    void revokeToken_savesRevokedToken() {
        when(refreshTokenRepository.findByTokenHash("h")).thenReturn(Optional.of(token));

        service.revokeToken("raw");

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    @DisplayName("verify uses the row-locked lookup and rejects a revoked token")
    void verify_rejectsRevokedToken() {
        token.revoke();
        when(refreshTokenRepository.findByTokenHashForUpdate("h")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verifyAndGet("raw")).isInstanceOf(TokenRefreshException.class);
        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }
}
