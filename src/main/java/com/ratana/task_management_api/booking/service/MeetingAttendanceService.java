package com.ratana.task_management_api.booking.service;

import com.ratana.task_management_api.booking.config.BookingProperties;
import com.ratana.task_management_api.booking.dto.response.AttendanceResponse;
import com.ratana.task_management_api.booking.dto.response.AttendanceResponse.CheckInResult;
import com.ratana.task_management_api.booking.dto.response.AttendanceResponse.Counts;
import com.ratana.task_management_api.booking.dto.response.AttendanceResponse.Entry;
import com.ratana.task_management_api.booking.dto.response.CheckInCodeResponse;
import com.ratana.task_management_api.booking.dto.response.CheckInCodeResponse.NextMeeting;
import com.ratana.task_management_api.booking.dto.response.CheckInCodeResponse.RoomDisplay;
import com.ratana.task_management_api.booking.enums.AttendanceStatus;
import com.ratana.task_management_api.booking.enums.CheckInMethod;
import com.ratana.task_management_api.common.exception.BadRequestException;
import com.ratana.task_management_api.booking.exception.BookingStateException;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.booking.mapper.RoomBookingMapper;
import com.ratana.task_management_api.booking.entity.MeetingAttendance;
import com.ratana.task_management_api.booking.entity.MeetingBooking;
import com.ratana.task_management_api.booking.entity.Room;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.booking.repository.MeetingAttendanceRepository;
import com.ratana.task_management_api.booking.repository.MeetingBookingRepository;
import com.ratana.task_management_api.booking.repository.RoomRepository;
import com.ratana.task_management_api.user.repository.UserRepository;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Meeting attendance: attendees check in by scanning the meeting's rotating QR code; the
 * organizer (or bookings:manage:any) shows that code and keeps a checklist they can correct.
 * Invitees still unknown after the meeting are marked ABSENT by {@link #finalizeAttendance}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingAttendanceService {

    private static final String UNKNOWN = "UNKNOWN";

    private final MeetingBookingRepository bookingRepository;
    private final MeetingAttendanceRepository attendanceRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final BookingAuthorizationService authorization;
    private final CheckInCodeService codeService;
    private final BookingTimePolicy timePolicy;
    private final BookingProperties properties;
    private final RoomBookingMapper mapper;

    // ---------------------------------------------------------------- QR codes

    /** Code for the organizer to show. Opening it also marks the organizer present. */
    @Transactional
    public CheckInCodeResponse checkInCode(UUID bookingId, UserPrincipal actor) {
        MeetingBooking booking = getBooking(bookingId);
        if (!authorization.canManage(actor, booking)) {
            throw new AccessDeniedException("Only the organizer can show the check-in code");
        }
        requireCheckInOpen(booking);
        if (booking.getOrganizer().getId().equals(actor.getId())) {
            markOrganizerPresent(booking);
        }
        return toCode(booking);
    }

    /** For a display at the room's door: the meeting checking in now, and the next one. */
    @Transactional(readOnly = true)
    public RoomDisplay roomDisplay(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));
        Instant now = timePolicy.now();
        List<MeetingBooking> open = bookingRepository.findCheckInOpenInRoom(roomId, now, now.plus(opensBefore()));
        MeetingBooking current = open.isEmpty() ? null : open.get(0);
        Instant after = current != null ? current.getStartAt() : now;
        NextMeeting next = bookingRepository.findNextInRoom(roomId, after)
                .map(b -> new NextMeeting(b.getId(), b.getTitle(), timePolicy.localDate(b.getStartAt()).toString(),
                        timePolicy.localTime(b.getStartAt()), timePolicy.localTime(b.getEndAt())))
                .orElse(null);
        return new RoomDisplay(mapper.toRoomSummary(room), current != null ? toCode(current) : null, next);
    }

    /** An attendee scanned a code. Idempotent: scanning twice keeps the first check-in. */
    @Transactional
    public CheckInResult checkIn(String scannedCode, UserPrincipal actor) {
        CheckInCodeService.Scanned scanned = codeService.parse(scannedCode);
        Instant now = timePolicy.now();
        if (!codeService.isValid(scanned, now)) {
            throw new BadRequestException("This QR code has expired. Scan the code on screen again.");
        }
        MeetingBooking booking = getBooking(scanned.bookingId());
        boolean invited = booking.getAttendees().stream().anyMatch(u -> u.getId().equals(actor.getId()));
        if (!invited) {
            throw new AccessDeniedException("You are not invited to this meeting");
        }
        requireCheckInOpen(booking);

        Optional<MeetingAttendance> existing = attendanceRepository.findByBookingIdAndUserId(booking.getId(), actor.getId());
        if (existing.isPresent() && isCheckedIn(existing.get().getStatus())) {
            MeetingAttendance row = existing.get();
            return new CheckInResult(booking.getId(), booking.getTitle(), roomName(booking), booking.locationLabel(),
                    row.getStatus().name(), row.getCheckedInAt(), true);
        }

        AttendanceStatus status = now.isAfter(booking.getStartAt().plus(Duration.ofMinutes(properties.lateAfterMinutes())))
                ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
        MeetingAttendance row = existing.orElseGet(() -> newRow(booking, actor.getId()));
        row.setStatus(status);
        row.setMethod(CheckInMethod.QR);
        row.setCheckedInAt(now);
        row.setMarkedBy(null);
        attendanceRepository.save(row);
        log.info("Check-in: booking={} user={} status={}", booking.getId(), actor.getId(), status);
        return new CheckInResult(booking.getId(), booking.getTitle(), roomName(booking), booking.locationLabel(),
                status.name(), now, false);
    }

    // ---------------------------------------------------------------- Checklist

    @Transactional(readOnly = true)
    public AttendanceResponse attendance(UUID bookingId, UserPrincipal actor) {
        MeetingBooking booking = getBooking(bookingId);
        if (!authorization.canView(actor, booking)) {
            throw new AccessDeniedException("You are not invited to this meeting");
        }
        return toResponse(booking, actor);
    }

    /** Organizer sets one invitee's status; UNKNOWN clears it. */
    @Transactional
    public AttendanceResponse mark(UUID bookingId, UUID userId, String status, String note, UserPrincipal actor) {
        MeetingBooking booking = getBooking(bookingId);
        if (!authorization.canManage(actor, booking)) {
            throw new AccessDeniedException("Only the organizer can update attendance");
        }
        if (!canMarkNow(booking)) {
            throw new BookingStateException("Attendance can be updated from %d minutes before the meeting until %d days after it"
                    .formatted(properties.checkInOpensMinutes(), properties.markWindowDays()));
        }
        boolean invited = booking.getAttendees().stream().anyMatch(u -> u.getId().equals(userId));
        if (!invited) {
            throw new BadRequestException("That person is not invited to this meeting");
        }

        Optional<MeetingAttendance> existing = attendanceRepository.findByBookingIdAndUserId(bookingId, userId);
        if (UNKNOWN.equals(status)) {
            existing.ifPresent(attendanceRepository::delete);
        } else {
            AttendanceStatus next = AttendanceStatus.valueOf(status);
            MeetingAttendance row = existing.orElseGet(() -> newRow(booking, userId));
            row.setStatus(next);
            row.setMethod(CheckInMethod.ORGANIZER);
            row.setMarkedBy(userRepository.getReferenceById(actor.getId()));
            row.setNote(StringUtils.hasText(note) ? note.trim() : null);
            if (isCheckedIn(next) && row.getCheckedInAt() == null) {
                row.setCheckedInAt(timePolicy.now());
            } else if (!isCheckedIn(next)) {
                row.setCheckedInAt(null);
            }
            attendanceRepository.save(row);
        }
        attendanceRepository.flush();
        log.info("Attendance marked: booking={} user={} status={} by={}", bookingId, userId, status, actor.getId());
        return toResponse(booking, actor);
    }

    // ---------------------------------------------------------------- After the meeting

    /** Writes ABSENT for every invitee still without a record once the meeting is over. */
    @Transactional
    public int finalizeAttendance(UUID bookingId) {
        MeetingBooking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null || booking.getAttendanceFinalizedAt() != null || !booking.isConfirmed()) {
            return 0;
        }
        Map<UUID, MeetingAttendance> rows = rowsByUser(bookingId);
        int marked = 0;
        for (User attendee : booking.getAttendees()) {
            if (!rows.containsKey(attendee.getId())) {
                MeetingAttendance row = newRow(booking, attendee.getId());
                row.setStatus(AttendanceStatus.ABSENT);
                row.setMethod(CheckInMethod.AUTO);
                attendanceRepository.save(row);
                marked++;
            }
        }
        booking.setAttendanceFinalizedAt(timePolicy.now());
        bookingRepository.save(booking);
        return marked;
    }

    public Instant finalizeCutoff() {
        return timePolicy.now().minus(Duration.ofMinutes(properties.absentAfterEndMinutes()));
    }

    // ---------------------------------------------------------------- Helpers

    private MeetingBooking getBooking(UUID id) {
        return bookingRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
    }

    private Duration opensBefore() {
        return Duration.ofMinutes(properties.checkInOpensMinutes());
    }

    private boolean isCheckInOpen(MeetingBooking booking) {
        Instant now = timePolicy.now();
        return booking.isConfirmed()
                && !now.isBefore(booking.getStartAt().minus(opensBefore()))
                && !now.isAfter(booking.getEndAt());
    }

    private void requireCheckInOpen(MeetingBooking booking) {
        if (!booking.isConfirmed()) {
            throw new BookingStateException("This meeting was cancelled");
        }
        Instant now = timePolicy.now();
        if (now.isBefore(booking.getStartAt().minus(opensBefore()))) {
            throw new BookingStateException("Check-in opens %d minutes before the meeting starts (%s)"
                    .formatted(properties.checkInOpensMinutes(), timePolicy.localTime(booking.getStartAt())));
        }
        if (now.isAfter(booking.getEndAt())) {
            throw new BookingStateException("This meeting has ended; check-in is closed");
        }
    }

    private boolean canMarkNow(MeetingBooking booking) {
        Instant now = timePolicy.now();
        return booking.isConfirmed()
                && !now.isBefore(booking.getStartAt().minus(opensBefore()))
                && now.isBefore(booking.getEndAt().plus(Duration.ofDays(properties.markWindowDays())));
    }

    private void markOrganizerPresent(MeetingBooking booking) {
        UUID organizerId = booking.getOrganizer().getId();
        if (attendanceRepository.findByBookingIdAndUserId(booking.getId(), organizerId).isPresent()) {
            return;
        }
        MeetingAttendance row = newRow(booking, organizerId);
        row.setStatus(AttendanceStatus.PRESENT);
        row.setMethod(CheckInMethod.AUTO);
        row.setCheckedInAt(timePolicy.now());
        attendanceRepository.save(row);
    }

    private MeetingAttendance newRow(MeetingBooking booking, UUID userId) {
        MeetingAttendance row = new MeetingAttendance();
        row.setBooking(booking);
        row.setUser(userRepository.getReferenceById(userId));
        return row;
    }

    private static boolean isCheckedIn(AttendanceStatus status) {
        return status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE;
    }

    private Map<UUID, MeetingAttendance> rowsByUser(UUID bookingId) {
        return attendanceRepository.findByBookingId(bookingId).stream()
                .collect(Collectors.toMap(row -> row.getUser().getId(), Function.identity()));
    }

    private CheckInCodeResponse toCode(MeetingBooking booking) {
        CheckInCodeService.Code code = codeService.issue(booking.getId(), timePolicy.now());
        long checkedIn = attendanceRepository.findByBookingId(booking.getId()).stream()
                .filter(row -> isCheckedIn(row.getStatus()))
                .count();
        return new CheckInCodeResponse(booking.getId(), booking.getTitle(), roomName(booking), booking.locationLabel(),
                timePolicy.localTime(booking.getStartAt()), timePolicy.localTime(booking.getEndAt()),
                code.payload(), code.expiresAt(), code.refreshSeconds(), (int) checkedIn, booking.getAttendeeCount());
    }

    private AttendanceResponse toResponse(MeetingBooking booking, UserPrincipal actor) {
        Map<UUID, MeetingAttendance> rows = rowsByUser(booking.getId());
        // Load attendees with departments in one query (names shown in the list).
        List<UUID> attendeeIds = booking.getAttendees().stream().map(User::getId).toList();
        List<User> attendees = attendeeIds.isEmpty() ? List.of() : userRepository.findAllWithDepartmentByIdIn(attendeeIds);
        UUID organizerId = booking.getOrganizer().getId();

        int present = 0;
        int late = 0;
        int absent = 0;
        int excused = 0;
        int unknown = 0;
        List<Entry> entries = new ArrayList<>();
        for (User user : attendees) {
            MeetingAttendance row = rows.get(user.getId());
            String status = row != null ? row.getStatus().name() : UNKNOWN;
            switch (status) {
                case "PRESENT" -> present++;
                case "LATE" -> late++;
                case "ABSENT" -> absent++;
                case "EXCUSED" -> excused++;
                default -> unknown++;
            }
            entries.add(new Entry(mapper.toPerson(user), status, user.getId().equals(organizerId),
                    row != null ? row.getCheckedInAt() : null,
                    row != null ? row.getMethod() : null,
                    row != null && row.getMarkedBy() != null ? row.getMarkedBy().getFullName() : null,
                    row != null ? row.getNote() : null));
        }
        // Organizer first, then by name.
        entries.sort(Comparator.comparing((Entry e) -> !e.organizer())
                .thenComparing(e -> e.user().fullName(), String.CASE_INSENSITIVE_ORDER));

        return new AttendanceResponse(booking.getId(),
                authorization.canManage(actor, booking) && canMarkNow(booking),
                isCheckInOpen(booking),
                booking.getStartAt().minus(opensBefore()),
                booking.getEndAt(),
                booking.getAttendanceFinalizedAt(),
                new Counts(present, late, absent, excused, unknown, entries.size()),
                entries);
    }

    private static String roomName(MeetingBooking booking) {
        return booking.getRoom() != null ? booking.getRoom().getName() : null;
    }
}
