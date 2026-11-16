package com.ratana.task_management_api.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** One room's day: booked slots and the free gaps between them within the displayed day range (workdayStart-workdayEnd); bookings themselves may be at any time. */
public record RoomScheduleResponse(
        UUID roomId,
        String roomName,
        int capacity,
        LocalDate date,
        @JsonFormat(pattern = "HH:mm") LocalTime workdayStart,
        @JsonFormat(pattern = "HH:mm") LocalTime workdayEnd,
        List<BusySlot> busy,
        List<FreeSlot> free
) {

    public record BusySlot(UUID bookingId, String title, String organizerName,
                           @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                           @JsonFormat(pattern = "HH:mm") LocalTime endTime) {
    }

    public record FreeSlot(@JsonFormat(pattern = "HH:mm") LocalTime startTime,
                           @JsonFormat(pattern = "HH:mm") LocalTime endTime) {
    }
}
