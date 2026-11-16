package com.ratana.task_management_api.booking.controller;

import com.ratana.task_management_api.booking.dto.request.BookingFilterRequest;
import com.ratana.task_management_api.booking.dto.request.BookingPreviewRequest;
import com.ratana.task_management_api.booking.dto.request.BookingRequest;
import com.ratana.task_management_api.booking.dto.request.BookingScopeRequest;
import com.ratana.task_management_api.booking.dto.request.CancelBookingRequest;
import com.ratana.task_management_api.booking.dto.request.CheckInRequest;
import com.ratana.task_management_api.booking.dto.request.MarkAttendanceRequest;
import com.ratana.task_management_api.booking.dto.response.AttendanceResponse;
import com.ratana.task_management_api.booking.dto.response.CheckInCodeResponse;
import com.ratana.task_management_api.common.response.ApiResponse;
import com.ratana.task_management_api.booking.dto.response.BookingPreviewResponse;
import com.ratana.task_management_api.booking.dto.response.BookingResponse;
import com.ratana.task_management_api.booking.dto.response.BookingScopeResponse;
import com.ratana.task_management_api.common.pagination.PageResponse;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.booking.service.MeetingBookingService;
import com.ratana.task_management_api.booking.service.BookingScopeService;
import com.ratana.task_management_api.booking.service.MeetingAttendanceService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class MeetingBookingController {

    private final MeetingBookingService bookingService;
    private final BookingScopeService scopeService;
    private final MeetingAttendanceService attendanceService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Book a room",
            description = "date + startTime/endTime are business-zone wall clock (e.g. 2026-11-12, 15:00-16:00). "
                    + "Targets (departments, positions, users) are combined; the organizer always attends. "
                    + "Rejected when outside your invite scope (403), over room capacity (422) or the room "
                    + "is taken (409). `warnings` lists attendees with another meeting at that time.")
    @PreAuthorize("hasAuthority('bookings:create')")
    public ResponseEntity<ApiResponse<BookingResponse>> create(@Valid @RequestBody BookingRequest request,
                                                               @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Room booked", bookingService.create(request, principal)));
    }

    @PostMapping(value = "/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Check a booking before saving",
            description = "Attendee count, capacity fit, room availability, scope problems and free rooms that fit.")
    @PreAuthorize("hasAuthority('bookings:create')")
    public ResponseEntity<ApiResponse<BookingPreviewResponse>> preview(
            @Valid @RequestBody BookingPreviewRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.preview(request, principal)));
    }

    @GetMapping("/me")
    @Operation(summary = "My meetings", description = "Meetings I organize or attend. Default sort: startAt")
    public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> myBookings(
            @ParameterObject @ModelAttribute BookingFilterRequest filter,
            @ParameterObject Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.myBookings(filter, pageable, principal)));
    }

    @GetMapping
    @Operation(summary = "All bookings (admin)", description = "Default sort: startAt")
    @PreAuthorize("hasAuthority('bookings:view:all')")
    public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> search(
            @ParameterObject @ModelAttribute BookingFilterRequest filter,
            @ParameterObject Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.search(filter, pageable, principal)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Booking detail with attendees", description = "Attendees, organizer, or bookings:view:all.")
    public ResponseEntity<ApiResponse<BookingResponse>> get(@PathVariable UUID id,
                                                            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.get(id, principal)));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Change a booking",
            description = "Organizer or bookings:manage:any, only before it starts. Attendees are notified.")
    public ResponseEntity<ApiResponse<BookingResponse>> update(@PathVariable UUID id,
                                                               @Valid @RequestBody BookingRequest request,
                                                               @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Booking updated", bookingService.update(id, request, principal)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a booking", description = "Organizer or bookings:manage:any, only before it starts.")
    public ResponseEntity<ApiResponse<BookingResponse>> cancel(@PathVariable UUID id,
                                                               @Valid @RequestBody(required = false) CancelBookingRequest request,
                                                               @AuthenticationPrincipal UserPrincipal principal) {
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(ApiResponse.success("Booking cancelled", bookingService.cancel(id, reason, principal)));
    }

    // ---------------------------------------------------------------- Attendance

    @PostMapping(value = "/check-in", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Check in by scanning the meeting QR code",
            description = "Send exactly what the scanner read. Works from 10 min before the start until the end; "
                    + "more than 10 min after the start counts as LATE. Scanning again changes nothing.")
    public ResponseEntity<ApiResponse<AttendanceResponse.CheckInResult>> checkIn(
            @Valid @RequestBody CheckInRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        AttendanceResponse.CheckInResult result = attendanceService.checkIn(request.getCode(), principal);
        return ResponseEntity.ok(ApiResponse.success(
                result.alreadyCheckedIn() ? "You were already checked in" : "Checked in", result));
    }

    @GetMapping("/{id}/check-in-code")
    @Operation(summary = "Rotating QR code for attendees to scan",
            description = "Organizer or bookings:manage:any, while check-in is open. Refetch every `refreshSeconds`. "
                    + "Opening it marks the organizer present.")
    public ResponseEntity<ApiResponse<CheckInCodeResponse>> checkInCode(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.checkInCode(id, principal)));
    }

    @GetMapping("/{id}/attendance")
    @Operation(summary = "Attendance checklist",
            description = "Every invitee with PRESENT / LATE / ABSENT / EXCUSED / UNKNOWN and counts. "
                    + "`canMark` says whether you may change it now.")
    public ResponseEntity<ApiResponse<AttendanceResponse>> attendance(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.attendance(id, principal)));
    }

    @PutMapping(value = "/{id}/attendance/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Mark one invitee",
            description = "Organizer or bookings:manage:any, from check-in opening until 7 days after the meeting. "
                    + "UNKNOWN clears the mark.")
    public ResponseEntity<ApiResponse<AttendanceResponse>> markAttendance(
            @PathVariable UUID id,
            @PathVariable UUID userId,
            @Valid @RequestBody MarkAttendanceRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Attendance updated",
                attendanceService.mark(id, userId, request.getStatus(), request.getNote(), principal)));
    }

    // ---------------------------------------------------------------- Invite scope (ABAC, admin)

    @GetMapping("/scopes/users/{userId}")
    @Operation(summary = "Departments a user may invite",
            description = "Used when the user holds bookings:invite:assigned (in addition to their own department).")
    @PreAuthorize("hasAuthority('bookings:scopes:manage')")
    public ResponseEntity<ApiResponse<BookingScopeResponse>> getScope(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(scopeService.get(userId)));
    }

    @PutMapping(value = "/scopes/users/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Set the departments a user may invite", description = "Replaces the whole list.")
    @PreAuthorize("hasAuthority('bookings:scopes:manage')")
    public ResponseEntity<ApiResponse<BookingScopeResponse>> setScope(
            @PathVariable UUID userId,
            @Valid @RequestBody BookingScopeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Booking scope updated",
                scopeService.replace(userId, request.getDepartmentIds(), principal.getId())));
    }
}
