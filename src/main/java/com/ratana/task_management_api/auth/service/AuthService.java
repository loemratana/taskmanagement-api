package com.ratana.task_management_api.auth.service;

import com.ratana.task_management_api.auth.dto.request.ChangePasswordRequest;
import com.ratana.task_management_api.auth.dto.request.LoginRequest;
import com.ratana.task_management_api.auth.dto.response.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public interface AuthService {


    AuthResponse login(LoginRequest loginRequest, HttpServletRequest httpServletRequest);
    AuthResponse  refreshAccessToken(@NotBlank String refreshTokenRequest);
    /**
     * Ends this device's session by revoking {@code refreshToken}. When the client sends no token
     * (older clients), falls back to revoking every session of the user.
     */
    void logout(UUID userId, String refreshToken);

    /**
     * Changes the caller's password, ends every existing session (all refresh tokens), and
     * returns fresh tokens so the device that made the change stays signed in.
     */
    AuthResponse changePassword(UUID userId, ChangePasswordRequest request, HttpServletRequest httpServletRequest);
}
