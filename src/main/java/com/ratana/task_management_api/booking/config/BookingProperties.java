package com.ratana.task_management_api.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Rules for meeting room bookings and room images ({@code app.booking.*}).
 * All wall-clock times are in {@link #zone()}, never the JVM default zone (UTC in Docker).
 */
@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(
        @DefaultValue("Asia/Phnom_Penh") ZoneId zone,
        @DefaultValue("07:00") LocalTime workdayStart,
        @DefaultValue("18:00") LocalTime workdayEnd,
        @DefaultValue("15") int slotMinutes,
        @DefaultValue("15") int minDurationMinutes,
        @DefaultValue("240") int maxDurationMinutes,
        @DefaultValue("60") int maxAdvanceDays,
        @DefaultValue("10") int maxImagesPerRoom,
        @DefaultValue("5242880") long maxImageSizeBytes,
        /* Check-in opens this many minutes before the start and stays open until the end. */
        @DefaultValue("10") int checkInOpensMinutes,
        /* Checking in this many minutes after the start counts as LATE. */
        @DefaultValue("10") int lateAfterMinutes,
        /* Invitees still unknown this long after the end are marked ABSENT. */
        @DefaultValue("15") int absentAfterEndMinutes,
        /* Lifetime of one rotating QR code (the previous one is also accepted). */
        @DefaultValue("30") int checkInCodeSeconds,
        /* How long after the meeting the organizer may still correct attendance. */
        @DefaultValue("7") int markWindowDays,
        /* HMAC key for QR codes; defaults to the JWT secret in application.yaml. */
        String checkInSecret
) {
}
