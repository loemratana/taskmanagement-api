package com.ratana.task_management_api.booking.service;

import com.ratana.task_management_api.booking.config.BookingProperties;
import com.ratana.task_management_api.booking.dto.response.RoomCalendarResponse;
import com.ratana.task_management_api.booking.dto.response.RoomCalendarResponse.DaySchedule;
import com.ratana.task_management_api.booking.dto.response.RoomScheduleResponse;
import com.ratana.task_management_api.booking.dto.response.RoomScheduleResponse.BusySlot;
import com.ratana.task_management_api.booking.dto.response.RoomScheduleResponse.FreeSlot;
import com.ratana.task_management_api.booking.dto.response.RoomSummaryResponse;
import com.ratana.task_management_api.booking.entity.MeetingBooking;
import com.ratana.task_management_api.booking.entity.Room;
import com.ratana.task_management_api.booking.enums.RoomStatus;
import com.ratana.task_management_api.booking.mapper.RoomBookingMapper;
import com.ratana.task_management_api.booking.repository.MeetingBookingRepository;
import com.ratana.task_management_api.booking.repository.RoomRepository;
import com.ratana.task_management_api.common.exception.BadRequestException;
import com.ratana.task_management_api.infrastructure.storage.StorageService;
import com.ratana.task_management_api.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceImplScheduleTest {

    private static final ZoneId PHNOM_PENH = ZoneId.of("Asia/Phnom_Penh");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);
    private static final LocalDate NEXT_DAY = DAY.plusDays(1);

    @Mock
    private RoomRepository roomRepository;
    @Mock
    private MeetingBookingRepository bookingRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private RoomBookingMapper mapper;

    private RoomServiceImpl service;
    private final User organizer = organizer();

    @BeforeEach
    void setUp() {
        BookingProperties properties = new BookingProperties(PHNOM_PENH, LocalTime.of(7, 0), LocalTime.of(18, 0),
                15, 15, 240, 60, 10, 5_242_880L, 10, 10, 15, 30, 7, "test-secret");
        BookingTimePolicy timePolicy = new BookingTimePolicy(properties,
                Clock.fixed(Instant.parse("2026-11-10T02:00:00Z"), ZoneOffset.UTC));
        service = new RoomServiceImpl(roomRepository, bookingRepository, storageService, mapper, timePolicy, properties);
    }

    @Test
    void singleDayScheduleListsBusySlotsAndFreeGaps() {
        Room room = room("Mekong");
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(bookingRepository.findRoomSchedule(eq(room.getId()), any(), any())).thenReturn(List.of(
                booking(room, DAY, "09:00", DAY, "10:00"),
                booking(room, DAY, "13:00", DAY, "14:30")));

        RoomScheduleResponse schedule = service.schedule(room.getId(), DAY);

        assertThat(schedule.busy()).extracting(BusySlot::startTime, BusySlot::endTime).containsExactly(
                org.assertj.core.groups.Tuple.tuple(t("09:00"), t("10:00")),
                org.assertj.core.groups.Tuple.tuple(t("13:00"), t("14:30")));
        assertThat(schedule.free()).containsExactly(
                new FreeSlot(t("07:00"), t("09:00")),
                new FreeSlot(t("10:00"), t("13:00")),
                new FreeSlot(t("14:30"), t("18:00")));
    }

    @Test
    void calendarSplitsBookingsPerRoomAndDayWithOneQuery() {
        Room mekong = room("Mekong");
        Room tonle = room("Tonle");
        when(roomRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(mekong, tonle));
        when(mapper.toRoomSummary(any())).thenAnswer(inv -> summary(inv.getArgument(0)));
        when(bookingRepository.findRoomsSchedule(anyCollection(), any(), any())).thenReturn(List.of(
                booking(mekong, DAY, "09:00", DAY, "10:00"),
                booking(mekong, NEXT_DAY, "15:00", NEXT_DAY, "16:00")));

        RoomCalendarResponse calendar = service.calendar(DAY, NEXT_DAY, null, null, null);

        assertThat(calendar.from()).isEqualTo(DAY);
        assertThat(calendar.to()).isEqualTo(NEXT_DAY);
        assertThat(calendar.rooms()).hasSize(2);

        List<DaySchedule> mekongDays = calendar.rooms().get(0).days();
        assertThat(mekongDays).extracting(DaySchedule::date).containsExactly(DAY, NEXT_DAY);
        assertThat(mekongDays.get(0).busy()).singleElement().extracting(BusySlot::startTime).isEqualTo(t("09:00"));
        assertThat(mekongDays.get(1).busy()).singleElement().extracting(BusySlot::startTime).isEqualTo(t("15:00"));

        DaySchedule tonleDay = calendar.rooms().get(1).days().get(0);
        assertThat(tonleDay.busy()).isEmpty();
        assertThat(tonleDay.free()).containsExactly(new FreeSlot(t("07:00"), t("18:00")));
    }

    @Test
    void meetingCrossingMidnightIsCutAtTheDayEdge() {
        Room room = room("Mekong");
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(mapper.toRoomSummary(any())).thenAnswer(inv -> summary(inv.getArgument(0)));
        when(bookingRepository.findRoomsSchedule(anyCollection(), any(), any())).thenReturn(List.of(
                booking(room, DAY, "22:00", NEXT_DAY, "01:00")));

        RoomCalendarResponse calendar = service.calendar(DAY, NEXT_DAY, room.getId(), null, null);

        List<DaySchedule> days = calendar.rooms().get(0).days();
        assertThat(days.get(0).busy()).singleElement()
                .extracting(BusySlot::startTime, BusySlot::endTime)
                .containsExactly(t("22:00"), LocalTime.MAX);
        assertThat(days.get(1).busy()).singleElement()
                .extracting(BusySlot::startTime, BusySlot::endTime)
                .containsExactly(LocalTime.MIDNIGHT, t("01:00"));
        assertThat(days.get(1).free()).containsExactly(new FreeSlot(t("07:00"), t("18:00")));
    }

    @Test
    void toDefaultsToFrom() {
        when(roomRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        RoomCalendarResponse calendar = service.calendar(DAY, null, null, null, null);

        assertThat(calendar.to()).isEqualTo(DAY);
        assertThat(calendar.rooms()).isEmpty();
        verify(bookingRepository, never()).findRoomsSchedule(anyCollection(), any(), any());
    }

    @Test
    void rejectsBadRanges() {
        assertThatThrownBy(() -> service.calendar(NEXT_DAY, DAY, null, null, null))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("on or after");
        assertThatThrownBy(() -> service.calendar(DAY, DAY.plusDays(RoomServiceImpl.MAX_CALENDAR_DAYS), null, null, null))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("at most 31 days");
    }

    // ---------------------------------------------------------------- Fixtures

    private static LocalTime t(String hhmm) {
        return LocalTime.parse(hhmm);
    }

    private static Room room(String name) {
        Room room = new Room();
        room.setId(UUID.randomUUID());
        room.setName(name);
        room.setCapacity(8);
        room.setStatus(RoomStatus.ACTIVE);
        return room;
    }

    private static RoomSummaryResponse summary(Room room) {
        return new RoomSummaryResponse(room.getId(), room.getName(), null, null, room.getCapacity(), null);
    }

    private static User organizer() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Dara");
        return user;
    }

    private MeetingBooking booking(Room room, LocalDate startDate, String start, LocalDate endDate, String end) {
        MeetingBooking booking = new MeetingBooking();
        booking.setId(UUID.randomUUID());
        booking.setTitle("Sync");
        booking.setRoom(room);
        booking.setOrganizer(organizer);
        booking.setStartAt(startDate.atTime(t(start)).atZone(PHNOM_PENH).toInstant());
        booking.setEndAt(endDate.atTime(t(end)).atZone(PHNOM_PENH).toInstant());
        return booking;
    }
}
