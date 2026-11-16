package com.ratana.task_management_api.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ratana.task_management_api.booking.dto.response.RoomScheduleResponse.BusySlot;
import com.ratana.task_management_api.booking.dto.response.RoomScheduleResponse.FreeSlot;
import com.ratana.task_management_api.booking.enums.RoomStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Several rooms over several days: for each room, each day's booked meetings and the free gaps
 * between them within workdayStart-workdayEnd. A meeting crossing midnight is cut at the day edge
 * (shown 00:00 at the start of a day, 23:59 at the end).
 */
public record RoomCalendarResponse(
        LocalDate from,
        LocalDate to,
        @JsonFormat(pattern = "HH:mm") LocalTime workdayStart,
        @JsonFormat(pattern = "HH:mm") LocalTime workdayEnd,
        List<RoomDays> rooms
) {

    public record RoomDays(RoomSummaryResponse room, RoomStatus status, List<DaySchedule> days) {
    }

    public record DaySchedule(LocalDate date, List<BusySlot> busy, List<FreeSlot> free) {
    }
}
