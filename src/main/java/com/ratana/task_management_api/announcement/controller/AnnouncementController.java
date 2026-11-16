package com.ratana.task_management_api.announcement.controller;

import com.ratana.task_management_api.announcement.dto.request.AnnouncementFilterRequest;
import com.ratana.task_management_api.announcement.dto.request.AnnouncementIdsRequest;
import com.ratana.task_management_api.announcement.dto.request.AnnouncementRequest;
import com.ratana.task_management_api.announcement.dto.response.AnnouncementAnalyticsResponse;
import com.ratana.task_management_api.announcement.dto.response.AnnouncementFeedResponse;
import com.ratana.task_management_api.announcement.dto.response.AnnouncementResponse;
import com.ratana.task_management_api.announcement.dto.response.AnnouncementStatsResponse;
import com.ratana.task_management_api.common.response.ApiResponse;
import com.ratana.task_management_api.common.pagination.PageResponse;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.announcement.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/announcements")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    // ---------------------------------------------------------------- Admin

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create announcement",
            description = "publishMode NOW publishes and notifies immediately; SCHEDULED waits for publishAt. "
                    + "Set draft=true to save without publishing.")
    @PreAuthorize("hasAuthority('announcements:create')")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> create(
            @Valid @RequestBody AnnouncementRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        AnnouncementResponse response = announcementService.create(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Announcement created", response));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search announcements",
            description = "All filters optional, combined with AND. Default sort: createdAt,desc")
    @PreAuthorize("hasAuthority('announcements:view')")
    public ResponseEntity<ApiResponse<PageResponse<AnnouncementResponse>>> search(
            @ParameterObject @ModelAttribute AnnouncementFilterRequest filter,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(announcementService.search(filter, pageable)));
    }

    @GetMapping("/stats")
    @Operation(summary = "Announcement dashboard counters")
    @PreAuthorize("hasAuthority('announcements:view')")
    public ResponseEntity<ApiResponse<AnnouncementStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.success(announcementService.getStats()));
    }

    @GetMapping("/analytics")
    @Operation(summary = "Announcement analytics (last 6 months)")
    @PreAuthorize("hasAuthority('announcements:view')")
    public ResponseEntity<ApiResponse<AnnouncementAnalyticsResponse>> analytics(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(announcementService.getAnalytics(principal.getId())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get announcement (admin view)")
    @PreAuthorize("hasAuthority('announcements:view')")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(announcementService.getById(id)));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update announcement",
            description = "Draft/scheduled: everything, and may publish. Published/expired: content, end date, pin "
                    + "and audience only, without re-notifying. Archived: rejected.")
    @PreAuthorize("hasAuthority('announcements:update')")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody AnnouncementRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Announcement updated",
                announcementService.update(id, request, principal.getId())));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish now", description = "Draft or scheduled only. Notifies the audience.")
    @PreAuthorize("hasAuthority('announcements:publish')")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> publish(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Announcement published",
                announcementService.publish(id, principal.getId())));
    }

    @PostMapping(value = "/bulk-publish", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Publish several", description = "Ids that aren't draft/scheduled are skipped.")
    @PreAuthorize("hasAuthority('announcements:publish')")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> bulkPublish(
            @Valid @RequestBody AnnouncementIdsRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        int published = announcementService.publishAll(request.getIds(), principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Announcements published", Map.of("published", published)));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive announcement", description = "Hides it from feeds and unpins it. Idempotent.")
    @PreAuthorize("hasAuthority('announcements:update')")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Announcement archived", announcementService.archive(id)));
    }

    @PostMapping("/{id}/duplicate")
    @Operation(summary = "Duplicate as draft")
    @PreAuthorize("hasAuthority('announcements:create')")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> duplicate(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Announcement duplicated",
                announcementService.duplicate(id, principal.getId())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete announcement", description = "Also removes its read history.")
    @PreAuthorize("hasAuthority('announcements:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        announcementService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Announcement deleted"));
    }

    @PostMapping(value = "/bulk-delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete several", description = "Unknown ids are ignored.")
    @PreAuthorize("hasAuthority('announcements:delete')")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> bulkDelete(
            @Valid @RequestBody AnnouncementIdsRequest request) {
        int deleted = announcementService.deleteAll(request.getIds());
        return ResponseEntity.ok(ApiResponse.success("Announcements deleted", Map.of("deleted", deleted)));
    }

    // ---------------------------------------------------------------- Employee feed (any signed-in user)

    @GetMapping("/feed")
    @Operation(summary = "My announcements",
            description = "Live announcements addressed to the caller, pinned first, newest first.")
    public ResponseEntity<ApiResponse<PageResponse<AnnouncementFeedResponse>>> feed(
            @ParameterObject Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(announcementService.getFeed(principal.getId(), pageable)));
    }

    @GetMapping("/feed/{id}")
    @Operation(summary = "Open an announcement from my feed", description = "Counts a view.")
    public ResponseEntity<ApiResponse<AnnouncementFeedResponse>> viewFromFeed(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(announcementService.viewFromFeed(id, principal.getId())));
    }

    @PostMapping("/feed/{id}/read")
    @Operation(summary = "Mark as read", description = "Idempotent; keeps the first read time.")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        announcementService.markRead(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Announcement marked as read"));
    }
}
