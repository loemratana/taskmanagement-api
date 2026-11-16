package com.ratana.task_management_api.notification.controller;

import com.ratana.task_management_api.notification.dto.request.NotificationRequest;
import com.ratana.task_management_api.notification.dto.request.DeviceRegistrationRequest;
import com.ratana.task_management_api.common.response.ApiResponse;
import com.ratana.task_management_api.notification.dto.response.NotificationResponse;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.notification.service.NotificationService;
import com.ratana.task_management_api.notification.service.UserDeviceService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    private static final int MAX_PAGE_SIZE = 100;

    @Operation(summary = "Get my notifications",
            description = "Paged like every other list endpoint: page is 0-based (page=0 is the first page), "
                    + "size is capped at 100.")
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

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction
    ) {

        // 0-based, like /tasks, /users and /submissions (it used to be the only 1-based list).
        if (page < 0) {
            page = 0;
        }

        if (size < 1) {
            size = 20;
        }
        size = Math.min(size, MAX_PAGE_SIZE);

        Set<String> allowedSortFields = Set.of(
                "id",
                "createdAt",
                "readAt"
        );

        if (!allowedSortFields.contains(sort)) {
            sort = "createdAt";
        }

        // fromString throws on junk input (500); an unknown direction just falls back to newest first.
        Sort.Direction sortDirection =
                Sort.Direction.fromOptionalString(direction).orElse(Sort.Direction.DESC);

        Pageable pageable = PageRequest.of(
                page,
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

    @Operation(summary = "Mark notification as unread")
    @PatchMapping("/{id}/unread")
    public ResponseEntity<ApiResponse<Void>> markAsUnread(
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {

        notificationService.markAsUnread(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mark all of my notifications as read", description = "Returns how many were updated.")
    @PatchMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllAsRead(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {

        int updated = notificationService.markAllAsRead(principal.getId());
        return ApiResponse.success(Map.of("updated", updated));
    }

    @Operation(summary = "Delete one of my notifications")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {

        notificationService.delete(id, principal.getId());
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

    @Operation(summary = "Send test notification (admin only, to yourself)")
    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<NotificationResponse> testNotification(
            @Valid @RequestBody NotificationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {

        // Only title/message/type/priority come from the client. Routing fields would let a caller
        // push task or submission content into any department's Telegram group, or spoof the sender.
        request.setRecipientId(principal.getId());
        request.setSenderId(principal.getId());
        request.setForcedChannels(null);
        request.setAllowedChannels(null);
        request.setReferenceType(null);
        request.setReferenceId(null);
        request.setMetadata(null);
        NotificationResponse response = notificationService.send(request);
        return ApiResponse.success("Test notification sent", response);
    }
}
