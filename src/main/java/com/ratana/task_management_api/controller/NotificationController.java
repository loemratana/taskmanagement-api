package com.ratana.task_management_api.controller;

import com.ratana.task_management_api.dto.NotificationRequest;
import com.ratana.task_management_api.dto.request.DeviceRegistrationRequest;
import com.ratana.task_management_api.dto.response.ApiResponse;
import com.ratana.task_management_api.dto.response.NotificationResponse;
import com.ratana.task_management_api.security.UserPrincipal;
import com.ratana.task_management_api.service.NotificationService;
import com.ratana.task_management_api.service.ServiceImpl.UserDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notification inbox and channel preferences")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserDeviceService userDeviceService;

    @Operation(summary = "Get my notifications")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Notifications retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping
    public ApiResponse<Page<NotificationResponse>> getMyNotifications(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal,

            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction
    ) {

        if (page < 1) {
            page = 1;
        }

        if (size < 1) {
            size = 20;
        }

        Set<String> allowedSortFields = Set.of(
                "id",
                "createdAt",
                "readAt"
        );

        if (!allowedSortFields.contains(sort)) {
            sort = "createdAt";
        }

        Sort.Direction sortDirection =
                Sort.Direction.fromString(direction);

        Pageable pageable = PageRequest.of(
                page - 1,
                size,
                Sort.by(sortDirection, sort)
        );

        Page<NotificationResponse> notifications =
                notificationService.getMyNotifications(
                        principal.getId(),
                        pageable
                );

        return ApiResponse.success(notifications);
    }

    @Operation(summary = "Get unread notification count")
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> getUnreadCount(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {

        long count = notificationService.getUnreadCount(principal.getId());
        return ApiResponse.success(Map.of("count", count));
    }

    @Operation(summary = "Mark notification as read")
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {

        notificationService.markAsRead(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Register device for push notifications")
    @PostMapping("/devices/register")
    public ApiResponse<Void> registerDevice(
            @Valid @RequestBody DeviceRegistrationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {

        userDeviceService.registerDevice(principal.getId(), request);
        return ApiResponse.success("Device registered successfully");
    }

    @Operation(summary = "Deactivate device")
    @DeleteMapping("/devices/{token}")
    public ResponseEntity<Void> deactivateDevice(
            @PathVariable String token,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {

        userDeviceService.deactivateDevice(principal.getId(), token);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Send test notification (current user only)")
    @PostMapping("/test")
    public ApiResponse<NotificationResponse> testNotification(
            @Valid @RequestBody NotificationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {

        request.setRecipientId(principal.getId());
        if (request.getSenderId() == null) {
            request.setSenderId(principal.getId());
        }
        NotificationResponse response = notificationService.send(request);
        return ApiResponse.success("Test notification sent", response);
    }
}
