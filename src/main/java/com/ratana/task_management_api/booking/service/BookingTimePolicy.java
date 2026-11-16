package com.ratana.task_management_api.booking.service;

import com.ratana.task_management_api.booking.config.BookingProperties;
import com.ratana.task_management_api.booking.enums.BookingTimeStatus;
import com.ratana.task_management_api.common.exception.BadRequestException;
import com.ratana.task_management_api.booking.entity.MeetingBooking;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Converts the wall-clock date/time a user picks ("2026-11-12, 15:00-16:00") into the
 * instants stored on a booking, and enforces the booking time rules from {@link BookingProperties}.
 * Always uses the configured business zone, never the JVM default.
 */
@Component
@RequiredArgsConstructor
public class BookingTimePolicy {

    private final BookingProperties properties;
    private final Clock clock;

    /** A validated booking window. */
    public record TimeWindow(Instant startAt, Instant endAt) {
    }

    public ZoneId zone() {
        return properties.zone();
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone()));
    }

    public Instant now() {
        return clock.instant();
    }

    /** Converts without the booking rules; for read-only checks such as previews and availability. */
    public TimeWindow toWindow(LocalDate date, LocalTime startTime, LocalTime endTime) {
        if (date == null || startTime == null || endTime == null) {
            throw new BadRequestException("date, startTime and endTime are required");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BadRequestException("endTime must be after startTime");
        }
        return new TimeWindow(toInstant(date, startTime), toInstant(date, endTime));
    }

    /** Converts and enforces every rule for creating or rescheduling a booking. */
    public TimeWindow validateForBooking(LocalDate date, LocalTime startTime, LocalTime endTime) {
        TimeWindow window = toWindow(date, startTime, endTime);

        int slot = properties.slotMinutes();
        if (!onSlot(startTime, slot) || !onSlot(endTime, slot)) {
            throw new BadRequestException(
                    "Start and end times must be on %d-minute steps (e.g. 15:00, 15:%02d)".formatted(slot, slot % 60));
        }

        long minutes = Duration.between(startTime, endTime).toMinutes();
        if (minutes < properties.minDurationMinutes() || minutes > properties.maxDurationMinutes()) {
            throw new BadRequestException("A meeting must last between %d and %d minutes"
                    .formatted(properties.minDurationMinutes(), properties.maxDurationMinutes()));
        }

        // No working-hours restriction: meetings may be booked at any time of day.
        // workdayStart/workdayEnd only frame the room schedule display.

        if (!window.startAt().isAfter(now())) {
            throw new BadRequestException("A meeting cannot start in the past");
        }

        LocalDate lastBookable = today().plusDays(properties.maxAdvanceDays());
        if (date.isAfter(lastBookable)) {
            throw new BadRequestException("Rooms can be booked at most %d days ahead (until %s)"
                    .formatted(properties.maxAdvanceDays(), lastBookable));
        }
        return window;
    }

    public Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(zone()).toInstant();
    }

    /** Exclusive end of {@code date}: local midnight of the next day. */
    public Instant endOfDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(zone()).toInstant();
    }

    public LocalDate localDate(Instant instant) {
        return instant.atZone(zone()).toLocalDate();
    }

    public LocalTime localTime(Instant instant) {
        return instant.atZone(zone()).toLocalTime();
    }

    public BookingTimeStatus timeStatus(MeetingBooking booking) {
        Instant now = now();
        if (now.isBefore(booking.getStartAt())) {
            return BookingTimeStatus.UPCOMING;
        }
        return now.isBefore(booking.getEndAt()) ? BookingTimeStatus.IN_PROGRESS : BookingTimeStatus.COMPLETED;
    }

    public LocalTime workdayStart() {
        return properties.workdayStart();
    }

    public LocalTime workdayEnd() {
        return properties.workdayEnd();
    }

    private Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(zone()).toInstant();
    }

    private static boolean onSlot(LocalTime time, int slotMinutes) {
        return time.getSecond() == 0 && time.getNano() == 0 && time.getMinute() % slotMinutes == 0;
    }
}
