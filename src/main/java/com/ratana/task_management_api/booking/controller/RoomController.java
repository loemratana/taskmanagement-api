package com.ratana.task_management_api.booking.controller;

import com.ratana.task_management_api.booking.dto.request.RoomFilterRequest;
import com.ratana.task_management_api.booking.dto.request.RoomImageOrderRequest;
import com.ratana.task_management_api.booking.dto.request.RoomRequest;
import com.ratana.task_management_api.common.response.ApiResponse;
import com.ratana.task_management_api.booking.dto.response.CheckInCodeResponse;
import com.ratana.task_management_api.common.pagination.PageResponse;
import com.ratana.task_management_api.booking.dto.response.RoomCalendarResponse;
import com.ratana.task_management_api.booking.dto.response.RoomResponse;
import com.ratana.task_management_api.booking.dto.response.RoomScheduleResponse;
import com.ratana.task_management_api.booking.enums.RoomFacility;
import com.ratana.task_management_api.booking.service.RoomService;
import com.ratana.task_management_api.booking.service.MeetingAttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final MeetingAttendanceService attendanceService;

    // ---------------------------------------------------------------- Read

    @GetMapping
    @Operation(summary = "Search rooms", description = "Filters optional. Default sort: name")
    @PreAuthorize("hasAuthority('rooms:view')")
    public ResponseEntity<ApiResponse<PageResponse<RoomResponse>>> search(
            @ParameterObject @ModelAttribute RoomFilterRequest filter,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(roomService.search(filter, pageable)));
    }

    @GetMapping("/available")
    @Operation(summary = "Rooms free for a time slot",
            description = "Active rooms with no confirmed booking overlapping date startTime-endTime, smallest first.")
    @PreAuthorize("hasAuthority('rooms:view')")
    public ResponseEntity<ApiResponse<List<RoomResponse>>> available(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) RoomFacility facility) {
        return ResponseEntity.ok(ApiResponse.success(
                roomService.findAvailable(date, startTime, endTime, minCapacity, facility)));
    }

    @GetMapping("/schedule")
    @Operation(summary = "Room calendar: booked and free slots per room per day",
            description = "from-to inclusive (to defaults to from, max 31 days). With roomId: that room only; "
                    + "otherwise every ACTIVE room matching minCapacity/facility, sorted by name. "
                    + "Each day lists confirmed meetings (title, organizer, HH:mm) and free gaps within the workday.")
    @PreAuthorize("hasAuthority('rooms:view')")
    public ResponseEntity<ApiResponse<RoomCalendarResponse>> calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) RoomFacility facility) {
        return ResponseEntity.ok(ApiResponse.success(roomService.calendar(from, to, roomId, minCapacity, facility)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get room with images")
    @PreAuthorize("hasAuthority('rooms:view')")
    public ResponseEntity<ApiResponse<RoomResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(roomService.get(id)));
    }

    @GetMapping("/{id}/schedule")
    @Operation(summary = "A room's bookings and free slots for one day")
    @PreAuthorize("hasAuthority('rooms:view')")
    public ResponseEntity<ApiResponse<RoomScheduleResponse>> schedule(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(roomService.schedule(id, date)));
    }

    @GetMapping("/{id}/check-in-display")
    @Operation(summary = "Room door display: current meeting's QR code and the next meeting",
            description = "For a screen at the room (rooms:display). `current` is null when no meeting is checking in; "
                    + "refetch every `current.refreshSeconds` (or ~30 s).")
    @PreAuthorize("hasAuthority('rooms:display')")
    public ResponseEntity<ApiResponse<CheckInCodeResponse.RoomDisplay>> checkInDisplay(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.roomDisplay(id)));
    }

    // ---------------------------------------------------------------- Manage

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create room")
    @PreAuthorize("hasAuthority('rooms:manage')")
    public ResponseEntity<ApiResponse<RoomResponse>> create(@Valid @RequestBody RoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Room created", roomService.create(request)));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update room",
            description = "Response `warnings` lists future bookings affected by a lower capacity or a non-ACTIVE status.")
    @PreAuthorize("hasAuthority('rooms:manage')")
    public ResponseEntity<ApiResponse<RoomResponse>> update(@PathVariable UUID id,
                                                            @Valid @RequestBody RoomRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Room updated", roomService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a room that was never booked", description = "Otherwise set status INACTIVE.")
    @PreAuthorize("hasAuthority('rooms:manage')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        roomService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Room deleted"));
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload room images",
            description = "One or more images (jpeg/png/webp/gif, max 5 MB each, max 10 per room). "
                    + "The first image of a room becomes its cover.")
    @PreAuthorize("hasAuthority('rooms:manage')")
    public ResponseEntity<ApiResponse<RoomResponse>> uploadImages(@PathVariable UUID id,
                                                                  @RequestPart("files") List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Images uploaded", roomService.uploadImages(id, files)));
    }

    @PatchMapping("/{id}/images/{imageId}/cover")
    @Operation(summary = "Make an image the room's cover")
    @PreAuthorize("hasAuthority('rooms:manage')")
    public ResponseEntity<ApiResponse<RoomResponse>> setCover(@PathVariable UUID id, @PathVariable UUID imageId) {
        return ResponseEntity.ok(ApiResponse.success(roomService.setCoverImage(id, imageId)));
    }

    @PutMapping(value = "/{id}/images/order", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reorder room images")
    @PreAuthorize("hasAuthority('rooms:manage')")
    public ResponseEntity<ApiResponse<RoomResponse>> reorder(@PathVariable UUID id,
                                                             @Valid @RequestBody RoomImageOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(roomService.reorderImages(id, request.getImageIds())));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @Operation(summary = "Delete a room image")
    @PreAuthorize("hasAuthority('rooms:manage')")
    public ResponseEntity<ApiResponse<RoomResponse>> deleteImage(@PathVariable UUID id, @PathVariable UUID imageId) {
        return ResponseEntity.ok(ApiResponse.success("Image deleted", roomService.deleteImage(id, imageId)));
    }
}
