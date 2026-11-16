package com.ratana.task_management_api.auth.service;

import com.ratana.task_management_api.auth.dto.request.ForgotPasswordRequest;
import com.ratana.task_management_api.auth.dto.request.ResetPasswordRequest;
import com.ratana.task_management_api.common.exception.InvalidRequestException;
import com.ratana.task_management_api.auth.entity.PasswordResetCode;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.auth.repository.PasswordResetCodeRepository;
import com.ratana.task_management_api.user.repository.UserRepository;
import com.ratana.task_management_api.auth.security.AuthRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    private static final String EMAIL = "emp@example.com";
    private static final String IP = "10.0.0.1";

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetCodeRepository codeRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AuthRateLimiter authRateLimiter;
    @Mock private PasswordResetMailer mailer;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private PasswordResetServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new PasswordResetServiceImpl(userRepository, codeRepository, passwordEncoder,
                refreshTokenService, authRateLimiter, mailer, 15, 5);
        user = spy(new User());
        user.setId(UUID.randomUUID());
        user.setEmail(EMAIL);
        user.setFullName("Emp One");
        user.setPasswordHash(passwordEncoder.encode("OldPassword1"));
        lenient().when(user.isAccountEnabled()).thenReturn(true);
    }

    @Test
    void requestReset_unknownEmail_doesNothingVisible() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        service.requestReset(new ForgotPasswordRequest(EMAIL), IP);

        verify(authRateLimiter).consumePasswordResetRequest(EMAIL, IP);
        verify(codeRepository, never()).save(any());
        verify(mailer, never()).sendCode(any(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void requestReset_emailsSixDigitCode_andStoresOnlyItsHash() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.requestReset(new ForgotPasswordRequest(EMAIL), IP);

        ArgumentCaptor<PasswordResetCode> saved = ArgumentCaptor.forClass(PasswordResetCode.class);
        verify(codeRepository).invalidateAllForUser(any(), any());
        verify(codeRepository).save(saved.capture());
        ArgumentCaptor<String> sentCode = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendCode(eq(user.getId()), eq(EMAIL), eq("Emp One"), sentCode.capture(), eq(15L));

        String code = sentCode.getValue();
        assertThat(code).matches("\\d{6}");
        assertThat(saved.getValue().getCodeHash()).isNotEqualTo(code);
        assertThat(passwordEncoder.matches(code, saved.getValue().getCodeHash())).isTrue();
        assertThat(saved.getValue().getExpiresAt()).isAfter(Instant.now().plusSeconds(14 * 60));
    }

    @Test
    void resetPassword_validCode_setsPasswordAndRevokesSessions() {
        PasswordResetCode code = usableCode("123456");

        service.resetPassword(new ResetPasswordRequest(EMAIL, "123456", "NewPassword1"));

        assertThat(code.getUsedAt()).isNotNull();
        assertThat(passwordEncoder.matches("NewPassword1", user.getPasswordHash())).isTrue();
        verify(refreshTokenService).revokeAllForUser(user.getId());
    }

    @Test
    void resetPassword_wrongCode_countsAttempt_andKeepsPassword() {
        PasswordResetCode code = usableCode("123456");
        String oldHash = user.getPasswordHash();

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest(EMAIL, "000000", "NewPassword1")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invalid or expired code");

        assertThat(code.getAttempts()).isEqualTo(1);
        assertThat(code.getUsedAt()).isNull();
        assertThat(user.getPasswordHash()).isEqualTo(oldHash);
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void resetPassword_fifthWrongCode_burnsTheCode() {
        PasswordResetCode code = usableCode("123456");
        code.setAttempts(4);

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest(EMAIL, "000000", "NewPassword1")))
                .isInstanceOf(InvalidRequestException.class);

        assertThat(code.getAttempts()).isEqualTo(5);
        assertThat(code.getUsedAt()).isNotNull();
    }

    @Test
    void resetPassword_noUsableCode_isRejected() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(codeRepository.findLatestUsable(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest(EMAIL, "123456", "NewPassword1")))
                .isInstanceOf(InvalidRequestException.class);
    }


    private PasswordResetCode usableCode(String plain) {
        PasswordResetCode code = PasswordResetCode.builder()
                .user(user)
                .codeHash(passwordEncoder.encode(plain))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(codeRepository.findLatestUsable(any(), any())).thenReturn(Optional.of(code));
        return code;
    }
}
