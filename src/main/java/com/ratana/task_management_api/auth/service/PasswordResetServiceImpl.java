package com.ratana.task_management_api.auth.service;

import com.ratana.task_management_api.auth.dto.request.ForgotPasswordRequest;
import com.ratana.task_management_api.auth.dto.request.ResetPasswordRequest;
import com.ratana.task_management_api.common.exception.InvalidRequestException;
import com.ratana.task_management_api.auth.entity.PasswordResetCode;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.auth.repository.PasswordResetCodeRepository;
import com.ratana.task_management_api.user.repository.UserRepository;
import com.ratana.task_management_api.auth.security.AuthRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    /** Same message for wrong, expired, used-up or unknown codes: no hint about which one it was. */
    private static final String INVALID_CODE = "Invalid or expired code";

    private final SecureRandom random = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuthRateLimiter authRateLimiter;
    private final PasswordResetMailer mailer;
    private final Duration codeTtl;
    private final int maxAttempts;

    public PasswordResetServiceImpl(
            UserRepository userRepository,
            PasswordResetCodeRepository codeRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            AuthRateLimiter authRateLimiter,
            PasswordResetMailer mailer,
            @Value("${app.security.password-reset.code-ttl-minutes:15}") long codeTtlMinutes,
            @Value("${app.security.password-reset.max-code-attempts:5}") int maxAttempts) {
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.authRateLimiter = authRateLimiter;
        this.mailer = mailer;
        this.codeTtl = Duration.ofMinutes(codeTtlMinutes);
        this.maxAttempts = maxAttempts;
    }

    @Override
    @Transactional
    public void requestReset(ForgotPasswordRequest request, String clientIp) {
        authRateLimiter.consumePasswordResetRequest(request.email(), clientIp);

        Optional<User> found = userRepository.findByEmail(request.email().trim())
                .filter(User::isAccountEnabled);
        String code = String.format("%06d", random.nextInt(1_000_000));
        // Hash even for an unknown email: hashing is the slow step, so both answers take the same time.
        String codeHash = passwordEncoder.encode(code);
        if (found.isEmpty()) {
            log.info("Password reset requested for unknown or disabled email {}", request.email());
            return;
        }

        User user = found.get();
        Instant now = Instant.now();
        codeRepository.invalidateAllForUser(user.getId(), now);
        codeRepository.save(PasswordResetCode.builder()
                .user(user)
                .codeHash(codeHash)
                .expiresAt(now.plus(codeTtl))
                .build());

        // Emailed in the background once the code is saved, so the request never waits on SMTP.
        Runnable email = () -> mailer.sendCode(user.getId(), user.getEmail(), user.getFullName(), code,
                codeTtl.toMinutes());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    email.run();
                }
            });
        } else {
            email.run();
        }
        log.info("Password reset code issued for user {}", user.getId());
    }

    @Override
    @Transactional(noRollbackFor = InvalidRequestException.class) // keep the attempt counter on a wrong code
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.email().trim())
                .filter(User::isAccountEnabled)
                .orElseThrow(() -> new InvalidRequestException(INVALID_CODE));

        PasswordResetCode resetCode = codeRepository.findLatestUsable(user.getId(), Instant.now())
                .orElseThrow(() -> new InvalidRequestException(INVALID_CODE));

        if (!passwordEncoder.matches(request.code(), resetCode.getCodeHash())) {
            resetCode.setAttempts(resetCode.getAttempts() + 1);
            if (resetCode.getAttempts() >= maxAttempts) {
                resetCode.setUsedAt(Instant.now()); // burned: the user has to request a new code
            }
            codeRepository.save(resetCode);
            log.warn("Wrong password reset code for user {} (attempt {}/{})",
                    user.getId(), resetCode.getAttempts(), maxAttempts);
            throw new InvalidRequestException(INVALID_CODE);
        }

        resetCode.setUsedAt(Instant.now());
        codeRepository.save(resetCode);

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
        log.info("Password reset for user {}; all sessions revoked", user.getId());
    }

    @Override
    @Transactional
    public int deleteExpiredCodes() {
        // Keep a day of history for troubleshooting, then drop.
        return codeRepository.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(1)));
    }
}
