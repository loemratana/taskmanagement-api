package com.ratana.task_management_api.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ratana.task_management_api.booking.enums.BookingStatus;
import com.ratana.task_management_api.booking.enums.BookingTimeStatus;
import com.ratana.task_management_api.booking.enums.MeetingLocationType;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * date/startTime/endTime are business-zone wall clock for display; startAt/endAt are the
 * exact instants (UTC) for calendars and countdowns.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingResponse(
        UUID id,
        String title,
        String description,
        MeetingLocationType locationType,
        /* Only for ROOM meetings. */
        RoomSummaryResponse room,
        BookingLocationResponse location,
        PersonRef organizer,
        LocalDate date,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        Instant startAt,
        Instant endAt,
        long durationMinutes,
        BookingStatus status,
        BookingTimeStatus timeStatus,
        int attendeeCount,
        Targets targets,
        /* Detail view only. */
        List<PersonRef> attendees,
        boolean canEdit,
        Instant cancelledAt,
        PersonRef cancelledBy,
        String cancelReason,
        Instant createdAt,
        Instant updatedAt,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> warnings
) {

    public record Ref(UUID id, String name) {
    }

    public record PositionRef(UUID id, String name, UUID departmentId, String departmentName) {
    }

    /** phoneNumber may be null; lets attendees and the organizer call each other from the meeting. */
    public record PersonRef(UUID id, String fullName, String avatarUrl, String departmentName, String phoneNumber) {
    }

    public record Targets(List<Ref> departments, List<PositionRef> positions, List<PersonRef> users) {
    }

    public BookingResponse withWarnings(List<String> newWarnings) {
        return new BookingResponse(id, title, description, locationType, room, location, organizer, date, startTime, endTime, startAt,
                endAt, durationMinutes, status, timeStatus, attendeeCount, targets, attendees, canEdit,
                cancelledAt, cancelledBy, cancelReason, createdAt, updatedAt, newWarnings);
    }
}
