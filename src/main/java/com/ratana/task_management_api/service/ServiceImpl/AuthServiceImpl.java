package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.dto.request.LoginRequest;
import com.ratana.task_management_api.dto.response.AuthResponse;
import com.ratana.task_management_api.dto.response.DeviceMetadata;
import com.ratana.task_management_api.dto.response.RefreshTokenResponse;
import com.ratana.task_management_api.model.User;
import com.ratana.task_management_api.repository.UserRepository;
import com.ratana.task_management_api.security.JwtTokenProvider;
import com.ratana.task_management_api.service.AuthService;
import com.ratana.task_management_api.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
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

    @Override
    public AuthResponse login(LoginRequest loginRequest, HttpServletRequest httpServletRequest) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password())
            );
        } catch (BadCredentialsException ex) {
            log.warn("Failed login attempt for email: {}", loginRequest.email());
            throw new BadCredentialsException("Invalid email or password");
        }
        User user = userRepository.findByEmail(loginRequest.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email "));


//        user.setLastLoginAt(Instant.now());


        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());

        DeviceMetadata metadata = DeviceMetadata.builder()
                .ipAddress(httpServletRequest.getRemoteAddr())
                .userAgent(httpServletRequest.getHeader("User-Agent"))
                .deviceType(resolveDeviceType(httpServletRequest.getHeader("User-Agent")))
                .deviceName(resolveDeviceName(httpServletRequest.getHeader("User-Agent")))
                .build();

        String refreshToken = refreshTokenService.createRefreshToken(user, metadata);
//        userRepository.save(user);


        return new AuthResponse(accessToken, refreshToken);
    }

    @Override
    public AuthResponse refreshAccessToken(@NotBlank String refreshTokenRequest) {
        // Validates the token exists, is not expired, and is not revoked
        RefreshTokenResponse tokenInfo = refreshTokenService.verifyAndGet(refreshTokenRequest);

        User user = userRepository.findById(tokenInfo.userId())
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getEmail());

        // Rotate: revoke the old token, issue a new one under the same device metadata
        refreshTokenService.revokeToken(refreshTokenRequest);

        DeviceMetadata deviceMetadata = DeviceMetadata.builder()
                .deviceName(tokenInfo.deviceName())
                .deviceType(tokenInfo.deviceType())
                .ipAddress(tokenInfo.ipAddress())
                .build();

        String newRefreshToken = refreshTokenService.createRefreshToken(user, deviceMetadata);

        return new AuthResponse(newAccessToken, newRefreshToken);
    }

    @Override
    public void logout(UUID id) {

        refreshTokenService.revokeAllForUser(id); // revoke by session/token ID, not user ID
        log.info("Session {} logged out", id);

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

    private String resolveClientIp(HttpServletRequest request) {
        // Behind a load balancer / reverse proxy, the real client IP is in X-Forwarded-For,
        // not request.getRemoteAddr() (which would return the proxy's IP).
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            // X-Forwarded-For can be a comma-separated chain; the first entry is the original client
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp;
        }

        return request.getRemoteAddr();
    }

}
