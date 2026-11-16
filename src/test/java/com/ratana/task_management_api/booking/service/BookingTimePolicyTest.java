package com.ratana.task_management_api.booking.service;

import com.ratana.task_management_api.booking.config.BookingProperties;
import com.ratana.task_management_api.booking.enums.BookingTimeStatus;
import com.ratana.task_management_api.common.exception.BadRequestException;
import com.ratana.task_management_api.booking.entity.MeetingBooking;
import com.ratana.task_management_api.booking.service.BookingTimePolicy.TimeWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTimePolicyTest {

    private static final ZoneId PHNOM_PENH = ZoneId.of("Asia/Phnom_Penh");
    /** 2026-11-10 09:00 in Phnom Penh (UTC+7). The clock itself is UTC, like in Docker. */
    private static final Instant NOW = Instant.parse("2026-11-10T02:00:00Z");
    private static final LocalDate TOMORROW = LocalDate.of(2026, 11, 11);

    private final BookingTimePolicy policy = new BookingTimePolicy(
            new BookingProperties(PHNOM_PENH, LocalTime.of(7, 0), LocalTime.of(18, 0),
                    15, 15, 240, 60, 10, 5_242_880L, 10, 10, 15, 30, 7, "test-secret"),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("15:00-16:00 local is stored as 08:00-09:00 UTC regardless of the JVM zone")
    void convertsWallClockInBusinessZone() {
        TimeWindow window = policy.validateForBooking(TOMORROW, LocalTime.of(15, 0), LocalTime.of(16, 0));

        assertThat(window.startAt()).isEqualTo(Instant.parse("2026-11-11T08:00:00Z"));
        assertThat(window.endAt()).isEqualTo(Instant.parse("2026-11-11T09:00:00Z"));
        assertThat(policy.localTime(window.startAt())).isEqualTo(LocalTime.of(15, 0));
        assertThat(policy.localDate(window.startAt())).isEqualTo(TOMORROW);
    }

    @Test
    void rejectsEndBeforeStart() {
        assertThatThrownBy(() -> policy.validateForBooking(TOMORROW, LocalTime.of(16, 0), LocalTime.of(15, 0)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("after");
    }

    @Test
    void rejectsTimesOffTheSlotGrid() {
        assertThatThrownBy(() -> policy.validateForBooking(TOMORROW, LocalTime.of(15, 7), LocalTime.of(16, 0)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("15-minute");
    }

    @Test
    void rejectsTooLongMeetings() {
        assertThatThrownBy(() -> policy.validateForBooking(TOMORROW, LocalTime.of(8, 0), LocalTime.of(12, 15)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("between 15 and 240");
    }

    @Test
    void allowsOutsideWorkingHours() {
        assertThatCode(() -> policy.validateForBooking(TOMORROW, LocalTime.of(17, 30), LocalTime.of(18, 30)))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.validateForBooking(TOMORROW, LocalTime.of(5, 0), LocalTime.of(6, 0)))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.validateForBooking(TOMORROW, LocalTime.of(21, 0), LocalTime.of(23, 45)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("08:00 today is already past at 09:00 local, even though the UTC clock reads 02:00")
    void rejectsPastStart() {
        LocalDate today = LocalDate.of(2026, 11, 10);
        assertThatThrownBy(() -> policy.validateForBooking(today, LocalTime.of(8, 0), LocalTime.of(8, 30)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("past");
        // Later today is fine.
        assertThat(policy.validateForBooking(today, LocalTime.of(10, 0), LocalTime.of(11, 0))).isNotNull();
    }

    @Test
    void rejectsTooFarAhead() {
        assertThatThrownBy(() -> policy.validateForBooking(TOMORROW.plusDays(60), LocalTime.of(9, 0), LocalTime.of(10, 0)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("60 days");
    }

    @Test
    void derivesTimeStatus() {
        MeetingBooking booking = new MeetingBooking();
        booking.setStartAt(NOW.minusSeconds(600));
        booking.setEndAt(NOW.plusSeconds(600));
        assertThat(policy.timeStatus(booking)).isEqualTo(BookingTimeStatus.IN_PROGRESS);

        booking.setStartAt(NOW.plusSeconds(60));
        booking.setEndAt(NOW.plusSeconds(3600));
        assertThat(policy.timeStatus(booking)).isEqualTo(BookingTimeStatus.UPCOMING);

        booking.setStartAt(NOW.minusSeconds(3600));
        booking.setEndAt(NOW);
        assertThat(policy.timeStatus(booking)).isEqualTo(BookingTimeStatus.COMPLETED);
    }
}
