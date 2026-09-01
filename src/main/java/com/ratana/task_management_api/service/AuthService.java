package com.ratana.task_management_api.service;

import com.ratana.task_management_api.dto.request.LoginRequest;
import com.ratana.task_management_api.dto.response.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public interface AuthService {


    AuthResponse login(LoginRequest loginRequest, HttpServletRequest httpServletRequest);
    AuthResponse  refreshAccessToken(@NotBlank String refreshTokenRequest);
    void logout(UUID id);
}
