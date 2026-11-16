package com.ratana.task_management_api.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * A rotating QR code for a meeting. Render {@code payload} as a QR code and fetch a new one
 * every {@code refreshSeconds} (the previous code stays valid for one more period).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckInCodeResponse(
        UUID bookingId,
        String title,
        /* Null unless the meeting is in one of our rooms. */
        String roomName,
        /* Where the meeting is, for any location type. */
        String location,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        String payload,
        Instant expiresAt,
        int refreshSeconds,
        int checkedInCount,
        int attendeeCount
) {

    /** For a room display: the meeting checking in now (if any) and the next one. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RoomDisplay(
            RoomSummaryResponse room,
            CheckInCodeResponse current,
            NextMeeting next
    ) {
    }

    public record NextMeeting(
            UUID bookingId,
            String title,
            String date,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime
    ) {
    }
}
