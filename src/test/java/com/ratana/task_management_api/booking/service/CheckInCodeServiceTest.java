package com.ratana.task_management_api.booking.service;

import com.ratana.task_management_api.booking.config.BookingProperties;
import com.ratana.task_management_api.common.exception.BadRequestException;
import com.ratana.task_management_api.booking.service.CheckInCodeService.Code;
import com.ratana.task_management_api.booking.service.CheckInCodeService.Scanned;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckInCodeServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T08:00:10Z");
    private final UUID bookingId = UUID.randomUUID();

    private static CheckInCodeService service(String secret) {
        return new CheckInCodeService(new BookingProperties(ZoneId.of("Asia/Phnom_Penh"),
                LocalTime.of(7, 0), LocalTime.of(18, 0), 15, 15, 240, 60, 10, 5_242_880L,
                10, 10, 15, 30, 7, secret));
    }

    @Test
    void acceptsTheCurrentCode() {
        CheckInCodeService codes = service("secret-a");
        Code code = codes.issue(bookingId, NOW);

        Scanned scanned = codes.parse(code.payload());
        assertThat(scanned.bookingId()).isEqualTo(bookingId);
        assertThat(codes.isValid(scanned, NOW)).isTrue();
        assertThat(code.expiresAt()).isEqualTo(Instant.parse("2026-11-11T08:00:30Z"));
    }

    @Test
    void acceptsThePreviousCodeButNotOlderOnes() {
        CheckInCodeService codes = service("secret-a");
        Scanned scanned = codes.parse(codes.issue(bookingId, NOW).payload());

        assertThat(codes.isValid(scanned, NOW.plusSeconds(30))).as("one rotation later").isTrue();
        assertThat(codes.isValid(scanned, NOW.plusSeconds(60))).as("two rotations later").isFalse();
    }

    @Test
    void rejectsACodeForAnotherBookingOrSecret() {
        CheckInCodeService codes = service("secret-a");
        Scanned scanned = codes.parse(codes.issue(bookingId, NOW).payload());

        Scanned otherBooking = new Scanned(UUID.randomUUID(), scanned.token());
        assertThat(codes.isValid(otherBooking, NOW)).isFalse();
        assertThat(service("secret-b").isValid(scanned, NOW)).isFalse();
    }

    @Test
    void rejectsPayloadsThatAreNotCheckInCodes() {
        CheckInCodeService codes = service("secret-a");
        assertThatThrownBy(() -> codes.parse("https://example.com")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> codes.parse("tms-checkin:v1:not-a-uuid:abc")).isInstanceOf(BadRequestException.class);
    }
}
