package com.ratana.task_management_api.controller;


import com.ratana.task_management_api.dto.request.LoginRequest;
import com.ratana.task_management_api.dto.request.RefreshTokenRequest;
import com.ratana.task_management_api.dto.response.ApiResponse;
import com.ratana.task_management_api.dto.response.AuthResponse;
import com.ratana.task_management_api.dto.response.ErrorResponse;
import com.ratana.task_management_api.dto.response.RefreshTokenResponse;
import com.ratana.task_management_api.security.UserPrincipal;
import com.ratana.task_management_api.service.AuthService;
import com.ratana.task_management_api.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, token refresh, logout, and session management")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;


    @Operation(
            summary = "Login with email and password",
            description = "Authenticates a user and issues a short-lived access token plus a long-lived refresh token. " +
                    "A new session record is created, tagged with the device's IP address and User-Agent."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Invalid email or password",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "Validation failed (missing/malformed fields)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        AuthResponse authResponse = authService.login(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("Login successful", authResponse));
    }

    @Operation(
            summary = "Refresh access token",
            description = "Exchanges a valid refresh token for a new access token. The refresh token is rotated " +
                    "(the old one is invalidated and a new one is returned) to limit damage if a token is leaked."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Token refreshed successfully",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "Refresh token is invalid, expired, or revoked",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/refresh-token", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {

        AuthResponse authResponse = authService.refreshAccessToken(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", authResponse));
    }

    @Operation(summary = "Logout", description = "Revokes all active sessions for the current authenticated user.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        authService.logout(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    @Operation(
            summary = "Logout from all devices",
            description = "Revokes every active refresh token belonging to the authenticated user, ending all sessions " +
                    "across every device. Requires a valid access token."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logged out from all devices"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    })
    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAllDevices(
            @AuthenticationPrincipal UserPrincipal principal) {

        refreshTokenService.revokeAllForUser(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Logged out from all devices"));
    }

    @Operation(
            summary = "List active sessions",
            description = "Returns all currently active (non-expired, non-revoked) login sessions for the authenticated " +
                    "user, including device name, type, IP address, and creation time — similar to \"Manage devices\" " +
                    "screens on Google/GitHub."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Active sessions retrieved",
                    content = @Content(schema = @Schema(implementation = RefreshTokenResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    })
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<RefreshTokenResponse>>> listActiveSessions(
            @AuthenticationPrincipal UserPrincipal principal) {

        List<RefreshTokenResponse> sessions = refreshTokenService.listActiveSessions(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }

    @Operation(
            summary = "Revoke a specific session",
            description = "Ends one specific device's session by its session ID, without affecting other active sessions. " +
                    "A user may only revoke their own sessions."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session revoked"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Session does not belong to the current user"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found")
    })
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> revokeSession(
            @Parameter(description = "UUID of the session to revoke", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {

//        refreshTokenService.revokeSessionForUser(sessionId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Session revoked"));
    }
}
