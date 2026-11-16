package com.ratana.task_management_api.auth.service;

import com.ratana.task_management_api.auth.security.AuthRateLimiter;
import com.ratana.task_management_api.auth.dto.request.ChangePasswordRequest;
import com.ratana.task_management_api.auth.dto.request.LoginRequest;
import com.ratana.task_management_api.auth.dto.response.AuthResponse;
import com.ratana.task_management_api.auth.dto.response.DeviceMetadata;
import com.ratana.task_management_api.auth.dto.response.RefreshTokenResponse;
import com.ratana.task_management_api.auth.exception.TokenRefreshException;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.user.repository.UserRepository;
import com.ratana.task_management_api.infrastructure.security.JwtTokenProvider;
import com.ratana.task_management_api.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;


@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String UNKNOWN = "Unknown";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final AuthRateLimiter authRateLimiter;

    @Override
    public AuthResponse login(LoginRequest loginRequest, HttpServletRequest httpServletRequest) {
        String clientIp = httpServletRequest.getRemoteAddr();
        log.debug("Login attempt for email {} from {}", loginRequest.email(), clientIp);
        authRateLimiter.assertLoginAllowed(loginRequest.email(), clientIp);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password())
            );
        } catch (BadCredentialsException ex) {
            log.warn("Failed login attempt for email: {}", loginRequest.email());
            authRateLimiter.loginFailed(loginRequest.email(), clientIp);
            throw new BadCredentialsException("Invalid email or password");
        } catch (AccountStatusException ex) {
            // Disabled/locked accounts get the same answer as a wrong password, so the response
            // doesn't reveal which emails belong to deactivated staff.
            log.warn("Login rejected for disabled account {}: {}", loginRequest.email(), ex.getMessage());
            authRateLimiter.loginFailed(loginRequest.email(), clientIp);
            throw new BadCredentialsException("Invalid email or password");
        }
        User user = userRepository.findByEmail(loginRequest.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email "));


        authRateLimiter.loginSucceeded(loginRequest.email());
        user.setLastLoginAt(Instant.now());


        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());

        DeviceMetadata metadata = deviceMetadata(httpServletRequest);

        String refreshToken = refreshTokenService.createRefreshToken(user, metadata);
        userRepository.save(user);
        log.info("User {} ({}) logged in from {} on {}", user.getEmail(), user.getId(),
                metadata.getIpAddress(), metadata.getDeviceName());


        return new AuthResponse(accessToken, refreshToken);
    }

    @Override
    @Transactional(noRollbackFor = TokenRefreshException.class)   // keep the revoke-all for disabled users
    public AuthResponse refreshAccessToken(@NotBlank String refreshTokenRequest) {
        // One transaction: verify (row-locked), revoke and re-issue commit together, so a refresh
        // token can be exchanged exactly once.
        RefreshTokenResponse tokenInfo = refreshTokenService.verifyAndGet(refreshTokenRequest);

        User user = userRepository.findById(tokenInfo.userId())
                .orElseThrow(() -> {
                    log.warn("Token refresh rejected: user {} no longer exists", tokenInfo.userId());
                    return new BadCredentialsException("User no longer exists");
                });

        if (!user.isAccountEnabled()) {
            log.warn("Token refresh rejected: user {} is disabled", user.getId());
            refreshTokenService.revokeAllForUser(user.getId());
            throw new TokenRefreshException("Account is disabled. Please contact your administrator.");
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getEmail());

        // Rotate: revoke the old token, issue a new one under the same device metadata
        refreshTokenService.revokeToken(refreshTokenRequest);

        DeviceMetadata deviceMetadata = DeviceMetadata.builder()
                .deviceName(tokenInfo.deviceName())
                .deviceType(tokenInfo.deviceType())
                .ipAddress(tokenInfo.ipAddress())
                .build();

        String newRefreshToken = refreshTokenService.createRefreshToken(user, deviceMetadata);
        log.info("Access token refreshed for user {} ({})", user.getEmail(), user.getId());

        return new AuthResponse(newAccessToken, newRefreshToken);
    }

    @Override
    public void logout(UUID userId, String refreshToken) {
        if (StringUtils.hasText(refreshToken)) {
            refreshTokenService.revokeTokenForUser(refreshToken, userId);
            log.info("User {} logged out of one session", userId);
            return;
        }
        // Legacy clients don't send their refresh token; we can't tell which session is theirs.
        refreshTokenService.revokeAllForUser(userId);
        log.info("User {} logged out without a refresh token; all sessions revoked", userId);
    }

    @Override
    public AuthResponse changePassword(UUID userId, ChangePasswordRequest request, HttpServletRequest httpServletRequest) {
        userService.changePassword(userId, request);

        // A changed password should end sessions on every other device.
        refreshTokenService.revokeAllForUser(userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));
        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
        String refreshToken = refreshTokenService.createRefreshToken(user, deviceMetadata(httpServletRequest));
        log.info("Password changed for user {}; all other sessions revoked", userId);

        return new AuthResponse(accessToken, refreshToken);
    }

    private DeviceMetadata deviceMetadata(HttpServletRequest httpServletRequest) {
        String userAgent = httpServletRequest.getHeader("User-Agent");
        return DeviceMetadata.builder()
                .ipAddress(httpServletRequest.getRemoteAddr())
                .userAgent(userAgent)
                .deviceType(resolveDeviceType(userAgent))
                .deviceName(resolveDeviceName(userAgent))
                .build();
    }

    private String resolveDeviceType(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return UNKNOWN;
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile")) {
            return "Mobile";
        }
        if (ua.contains("tablet")) {
            return "Tablet";
        }
        return "Desktop";
    }

    private String resolveDeviceName(String userAgent) {

        if (!StringUtils.hasText(userAgent)) {
            return "Unknown Device";
        }

        String ua = userAgent.toLowerCase();

        if (ua.contains("windows")) {
            return "Windows PC";
        }

        if (ua.contains("mac")) {
            return "Mac";
        }

        if (ua.contains("android")) {
            return "Android Device";
        }

        if (ua.contains("iphone")) {
            return "iPhone";
        }

        return "Unknown Device";
    }


}
