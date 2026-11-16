package com.ratana.task_management_api.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ratana.task_management_api.booking.dto.response.BookingResponse.PersonRef;
import com.ratana.task_management_api.booking.enums.CheckInMethod;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Attendance of one booking. {@code status} is PRESENT / LATE / ABSENT / EXCUSED, or
 * UNKNOWN for invitees who haven't checked in and weren't marked yet.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttendanceResponse(
        UUID bookingId,
        /* Whether the current user may use the checklist right now. */
        boolean canMark,
        /* Whether attendees can check in by QR right now. */
        boolean checkInOpen,
        /* When check-in opens / closes (start - opens, end). */
        Instant checkInOpensAt,
        Instant checkInClosesAt,
        /* Set once everyone still unknown after the meeting was marked absent. */
        Instant finalizedAt,
        Counts counts,
        List<Entry> attendees
) {

    /** Result of a QR check-in. */
    public record CheckInResult(
            UUID bookingId,
            String title,
            /* Null unless the meeting is in one of our rooms. */
            String roomName,
            /* Where the meeting is, for any location type: room name, place name or "Online meeting". */
            String location,
            String status,
            Instant checkedInAt,
            /* True when this user had already checked in; nothing changed. */
            boolean alreadyCheckedIn
    ) {
    }

    public record Counts(int present, int late, int absent, int excused, int unknown, int total) {
    }

    public record Entry(
            PersonRef user,
            String status,
            boolean organizer,
            Instant checkedInAt,
            CheckInMethod method,
            String markedByName,
            String note
    ) {
    }
}
