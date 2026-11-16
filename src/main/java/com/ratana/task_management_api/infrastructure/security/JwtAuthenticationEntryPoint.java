package com.ratana.task_management_api.infrastructure.security;

import tools.jackson.databind.ObjectMapper;
import com.ratana.task_management_api.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Without this, Spring Security falls back to Http403ForbiddenEntryPoint (no httpBasic/formLogin
 * is registered to supply a default), so missing/expired/invalid access tokens return 403 —
 * indistinguishable from an authenticated-but-not-permitted (hasRole) denial. Clients need 401
 * here specifically so they know to attempt a token refresh rather than treat it as a hard denial.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Full authentication is required to access this resource",
                request.getRequestURI()
        );
        objectMapper.writeValue(response.getWriter(), body);
    }
}
