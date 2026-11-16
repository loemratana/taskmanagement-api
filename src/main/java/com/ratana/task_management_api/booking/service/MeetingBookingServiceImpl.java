package com.ratana.task_management_api.booking.service;

import com.ratana.task_management_api.booking.dto.request.BookingFilterRequest;
import com.ratana.task_management_api.booking.dto.request.BookingPreviewRequest;
import com.ratana.task_management_api.booking.dto.request.BookingRequest;
import com.ratana.task_management_api.booking.dto.response.BookingPreviewResponse;
import com.ratana.task_management_api.booking.dto.response.BookingResponse;
import com.ratana.task_management_api.common.pagination.PageResponse;
import com.ratana.task_management_api.booking.dto.response.RoomSummaryResponse;
import com.ratana.task_management_api.booking.enums.BookingStatus;
import com.ratana.task_management_api.booking.enums.BookingTimeStatus;
import com.ratana.task_management_api.notification.enums.NotificationType;
import com.ratana.task_management_api.booking.event.MeetingBookingEvent;
import com.ratana.task_management_api.common.exception.BadRequestException;
import com.ratana.task_management_api.booking.exception.BookingConflictException;
import com.ratana.task_management_api.booking.exception.BookingStateException;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.booking.exception.RoomCapacityExceededException;
import com.ratana.task_management_api.booking.mapper.RoomBookingMapper;
import com.ratana.task_management_api.booking.entity.MeetingBooking;
import com.ratana.task_management_api.booking.entity.MeetingLocation;
import com.ratana.task_management_api.booking.entity.Room;
import com.ratana.task_management_api.booking.enums.MeetingLocationType;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.booking.repository.MeetingBookingRepository;
import com.ratana.task_management_api.booking.repository.RoomRepository;
import com.ratana.task_management_api.user.repository.UserRepository;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.booking.service.BookingAudienceResolver.ResolvedAudience;
import com.ratana.task_management_api.booking.service.BookingTimePolicy.TimeWindow;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingBookingServiceImpl implements MeetingBookingService {

    private static final String OVERLAP_CONSTRAINT = "no_room_overlap";

    private final MeetingBookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomService roomService;
    private final BookingAudienceResolver audienceResolver;
    private final BookingAuthorizationService authorization;
    private final BookingTimePolicy timePolicy;
    private final BookingLocationPolicy locationPolicy;
    private final RoomBookingMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    // ---------------------------------------------------------------- Commands

    @Override
    @Transactional
    public BookingResponse create(BookingRequest request, UserPrincipal actor) {
        Placement placement = resolvePlacement(request);
        TimeWindow window = timePolicy.validateForBooking(request.getDate(), request.getStartTime(), request.getEndTime());
        ResolvedAudience audience = audienceResolver.resolve(request.getTargets(), actor.getId());
        authorization.checkCanInvite(actor, audience);
        checkRoomRules(placement, audience, window, MeetingBookingRepository.NO_BOOKING);

        MeetingBooking booking = new MeetingBooking();
        booking.setOrganizer(userRepository.getReferenceById(actor.getId()));
        apply(booking, request, placement, window, audience);
        MeetingBooking saved = save(booking);

        Set<UUID> invited = new HashSet<>(audience.attendeeIds());
        invited.remove(actor.getId());
        publish(saved.getId(), actor.getId(), Map.of(NotificationType.MEETING_INVITED, invited),
                NotificationType.MEETING_INVITED);

        log.info("Booking created: id={} {} {}..{} attendees={} by={}", saved.getId(), placement,
                window.startAt(), window.endAt(), audience.attendeeCount(), actor.getId());
        return mapper.toBookingResponse(saved, false, true)
                .withWarnings(attendeeConflictWarnings(audience, window, saved.getId()));
    }

    @Override
    @Transactional
    public BookingResponse update(UUID id, BookingRequest request, UserPrincipal actor) {
        MeetingBooking booking = getBooking(id);
        requireEditable(booking, actor);

        Placement placement = resolvePlacement(request);
        TimeWindow window = timePolicy.validateForBooking(request.getDate(), request.getStartTime(), request.getEndTime());
        ResolvedAudience audience = audienceResolver.resolve(request.getTargets(), booking.getOrganizer().getId());
        authorization.checkCanInvite(actor, audience);
        checkRoomRules(placement, audience, window, booking.getId());

        Set<UUID> before = booking.getAttendees().stream().map(User::getId).collect(Collectors.toSet());
        apply(booking, request, placement, window, audience);
        MeetingBooking saved = save(booking);

        Set<UUID> after = audience.attendeeIds();
        Map<NotificationType, Set<UUID>> recipients = new EnumMap<>(NotificationType.class);
        recipients.put(NotificationType.MEETING_INVITED, minus(after, before, actor.getId()));
        recipients.put(NotificationType.MEETING_UPDATED, intersect(after, before, actor.getId()));
        recipients.put(NotificationType.MEETING_CANCELLED, minus(before, after, actor.getId()));
        publish(saved.getId(), actor.getId(), recipients, null);

        log.info("Booking updated: id={} {} {}..{} attendees={} by={}", id, placement,
                window.startAt(), window.endAt(), audience.attendeeCount(), actor.getId());
        return mapper.toBookingResponse(saved, false, true)
                .withWarnings(attendeeConflictWarnings(audience, window, saved.getId()));
    }

    @Override
    @Transactional
    public BookingResponse cancel(UUID id, String reason, UserPrincipal actor) {
        MeetingBooking booking = getBooking(id);
        requireEditable(booking, actor);

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(timePolicy.now());
        booking.setCancelledBy(userRepository.getReferenceById(actor.getId()));
        booking.setCancelReason(StringUtils.hasText(reason) ? reason.trim() : null);
        MeetingBooking saved = bookingRepository.save(booking);

        Set<UUID> attendees = booking.getAttendees().stream().map(User::getId).collect(Collectors.toSet());
        attendees.remove(actor.getId());
        publish(saved.getId(), actor.getId(), Map.of(NotificationType.MEETING_CANCELLED, attendees),
                NotificationType.MEETING_CANCELLED);

        log.info("Booking cancelled: id={} by={}", id, actor.getId());
        return mapper.toBookingResponse(saved, false, false);
    }

    // ---------------------------------------------------------------- Queries

    @Override
    @Transactional(readOnly = true)
    public BookingResponse get(UUID id, UserPrincipal actor) {
        MeetingBooking booking = getBooking(id);
        if (!authorization.canView(actor, booking)) {
            throw new AccessDeniedException("You are not invited to this meeting");
        }
        // Load attendees with their departments in one query instead of one per person.
        List<UUID> attendeeIds = booking.getAttendees().stream().map(User::getId).toList();
        if (!attendeeIds.isEmpty()) {
            userRepository.findAllWithDepartmentByIdIn(attendeeIds);
        }
        return mapper.toBookingResponse(booking, true, isEditable(booking, actor));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> myBookings(BookingFilterRequest filter, Pageable pageable, UserPrincipal actor) {
        Specification<MeetingBooking> spec = filterSpec(filter).and(attendedBy(actor.getId()));
        return page(spec, pageable, actor);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> search(BookingFilterRequest filter, Pageable pageable, UserPrincipal actor) {
        return page(filterSpec(filter), pageable, actor);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingPreviewResponse preview(BookingPreviewRequest request, UserPrincipal actor) {
        UUID excludeId = request.getExcludeBookingId() != null
                ? request.getExcludeBookingId() : MeetingBookingRepository.NO_BOOKING;
        UUID organizerId = actor.getId();
        if (request.getExcludeBookingId() != null) {
            MeetingBooking existing = getBooking(request.getExcludeBookingId());
            requireEditable(existing, actor);
            organizerId = existing.getOrganizer().getId();
        }

        ResolvedAudience audience = audienceResolver.resolve(request.getTargets(), organizerId);
        BookingPreviewResponse.BookingPreviewResponseBuilder result = BookingPreviewResponse.builder()
                .attendeeCount(audience.attendeeCount())
                .scopeViolations(authorization.scopeViolations(actor, audience));

        boolean roomMeeting = locationPolicy.typeOrDefault(request.getLocationType()) == MeetingLocationType.ROOM;
        Room room = null;
        if (roomMeeting && request.getRoomId() != null) {
            room = getRoom(request.getRoomId());
            result.roomCapacity(room.getCapacity())
                    .fitsRoom(audience.attendeeCount() <= room.getCapacity());
        }

        boolean hasTime = request.getDate() != null && request.getStartTime() != null && request.getEndTime() != null;
        if (hasTime) {
            TimeWindow window = timePolicy.toWindow(request.getDate(), request.getStartTime(), request.getEndTime());
            result.attendeesWithConflicts(bookingRepository.countAttendeesWithConflicts(
                    audience.attendeeIds(), window.startAt(), window.endAt(), excludeId));
            if (room != null) {
                result.roomAvailable(room.isBookable() && bookingRepository.findRoomConflicts(
                        room.getId(), window.startAt(), window.endAt(), excludeId).isEmpty());
            }
            if (roomMeeting) {
                List<RoomSummaryResponse> suggestions = roomService.findAvailable(request.getDate(),
                                request.getStartTime(), request.getEndTime(), audience.attendeeCount(), null).stream()
                        .map(r -> new RoomSummaryResponse(r.id(), r.name(), r.code(), r.location(), r.capacity(),
                                r.coverImageUrl()))
                        .limit(5)
                        .toList();
                result.suggestedRooms(suggestions);
            }
        }
        return result.build();
    }

    // ---------------------------------------------------------------- Rules

    /** Where the meeting will be. room is set only for ROOM; location may be null for ROOM. */
    private record Placement(MeetingLocationType type, Room room, MeetingLocation location) {
        @Override
        public String toString() {
            return room != null ? "type=ROOM room=" + room.getId() : "type=" + type;
        }
    }

    private Placement resolvePlacement(BookingRequest request) {
        MeetingLocationType type = locationPolicy.typeOrDefault(request.getLocationType());
        MeetingLocation location = locationPolicy.normalize(type, request.getLocation());
        if (type != MeetingLocationType.ROOM) {
            return new Placement(type, null, location);
        }
        if (request.getRoomId() == null) {
            throw new BadRequestException("Choose a room, or set locationType to EXTERNAL or ONLINE");
        }
        return new Placement(type, getBookableRoom(request.getRoomId()), location);
    }

    /** Capacity and double-booking only exist for our own rooms. */
    private void checkRoomRules(Placement placement, ResolvedAudience audience, TimeWindow window, UUID excludeId) {
        if (placement.room() != null) {
            checkCapacity(placement.room(), audience);
            checkRoomFree(placement.room(), window, excludeId);
        }
    }

    private Room getRoom(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));
    }

    private Room getBookableRoom(UUID roomId) {
        Room room = getRoom(roomId);
        if (!room.isBookable()) {
            throw new BadRequestException("Room '%s' is %s and cannot be booked"
                    .formatted(room.getName(), room.getStatus().name().toLowerCase()));
        }
        return room;
    }

    private MeetingBooking getBooking(UUID id) {
        return bookingRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
    }

    /** Over capacity is always rejected. */
    private static void checkCapacity(Room room, ResolvedAudience audience) {
        if (audience.attendeeCount() > room.getCapacity()) {
            throw new RoomCapacityExceededException(
                    "Room '%s' holds %d people, but this meeting has %d attendees (organizer included)"
                            .formatted(room.getName(), room.getCapacity(), audience.attendeeCount()));
        }
    }

    /** Friendly pre-check; the no_room_overlap DB constraint is the real guarantee under concurrency. */
    private void checkRoomFree(Room room, TimeWindow window, UUID excludeId) {
        List<MeetingBooking> conflicts = bookingRepository.findRoomConflicts(
                room.getId(), window.startAt(), window.endAt(), excludeId);
        if (!conflicts.isEmpty()) {
            MeetingBooking clash = conflicts.get(0);
            throw new BookingConflictException("Room '%s' is already booked %s-%s ('%s')".formatted(
                    room.getName(), timePolicy.localTime(clash.getStartAt()),
                    timePolicy.localTime(clash.getEndAt()), clash.getTitle()));
        }
    }

    private void requireEditable(MeetingBooking booking, UserPrincipal actor) {
        if (!authorization.canManage(actor, booking)) {
            throw new AccessDeniedException("Only the organizer can change this meeting");
        }
        if (!booking.isConfirmed()) {
            throw new BookingStateException("This meeting was cancelled");
        }
        if (timePolicy.timeStatus(booking) != BookingTimeStatus.UPCOMING) {
            throw new BookingStateException("This meeting has already started and can no longer be changed");
        }
    }

    private boolean isEditable(MeetingBooking booking, UserPrincipal actor) {
        return authorization.canManage(actor, booking) && booking.isConfirmed()
                && timePolicy.timeStatus(booking) == BookingTimeStatus.UPCOMING;
    }

    private void apply(MeetingBooking booking, BookingRequest request, Placement placement, TimeWindow window,
                       ResolvedAudience audience) {
        booking.setLocationType(placement.type());
        booking.setRoom(placement.room());
        booking.setLocation(placement.location());
        booking.setTitle(request.getTitle().trim());
        booking.setDescription(request.getDescription());
        booking.setStartAt(window.startAt());
        booking.setEndAt(window.endAt());
        replace(booking.getTargetDepartments(), audience.departments());
        replace(booking.getTargetPositions(), audience.positions());
        replace(booking.getTargetUsers(), audience.users());
        Set<User> attendees = audience.attendeeIds().stream()
                .map(userRepository::getReferenceById)
                .collect(Collectors.toSet());
        replace(booking.getAttendees(), attendees);
        booking.setAttendeeCount(audience.attendeeCount());
    }

    private MeetingBooking save(MeetingBooking booking) {
        try {
            return bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            String cause = String.valueOf(e.getMostSpecificCause().getMessage());
            if (cause.contains(OVERLAP_CONSTRAINT)) {
                throw new BookingConflictException(
                        "Room '%s' was just booked by someone else for this time".formatted(booking.getRoom().getName()));
            }
            throw e;
        }
    }

    private List<String> attendeeConflictWarnings(ResolvedAudience audience, TimeWindow window, UUID bookingId) {
        long busy = bookingRepository.countAttendeesWithConflicts(
                audience.attendeeIds(), window.startAt(), window.endAt(), bookingId);
        List<String> warnings = new ArrayList<>();
        if (busy > 0) {
            warnings.add("%d attendee(s) already have another meeting at this time".formatted(busy));
        }
        return warnings;
    }

    /**
     * @param groupAlert also announce in the targeted departments' Telegram groups (create/cancel), or null
     */
    private void publish(UUID bookingId, UUID actorId, Map<NotificationType, Set<UUID>> recipients,
                         NotificationType groupAlert) {
        Map<NotificationType, Set<UUID>> nonEmpty = recipients.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!nonEmpty.isEmpty() || groupAlert != null) {
            eventPublisher.publishEvent(new MeetingBookingEvent(bookingId, actorId, nonEmpty, groupAlert));
        }
    }

    private PageResponse<BookingResponse> page(Specification<MeetingBooking> spec, Pageable pageable,
                                               UserPrincipal actor) {
        Pageable sorted = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("startAt"));
        return PageResponse.from(bookingRepository.findAll(spec, sorted)
                .map(b -> mapper.toBookingResponse(b, false, isEditable(b, actor))));
    }

    private Specification<MeetingBooking> filterSpec(BookingFilterRequest filter) {
        Specification<MeetingBooking> spec = (root, query, cb) -> cb.conjunction();
        if (filter == null) {
            return spec;
        }
        if (filter.getFrom() != null && filter.getTo() != null && filter.getTo().isBefore(filter.getFrom())) {
            throw new BadRequestException("'to' must not be before 'from'");
        }
        if (filter.getLocationType() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("locationType"), filter.getLocationType()));
        }
        if (filter.getRoomId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("room").get("id"), filter.getRoomId()));
        }
        if (filter.getOrganizerId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("organizer").get("id"), filter.getOrganizerId()));
        }
        if (filter.getStatus() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), filter.getStatus()));
        }
        if (filter.getFrom() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThan(root.get("endAt"), timePolicy.startOfDay(filter.getFrom())));
        }
        if (filter.getTo() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThan(root.get("startAt"), timePolicy.endOfDay(filter.getTo())));
        }
        return spec;
    }

    private static Specification<MeetingBooking> attendedBy(UUID userId) {
        return (root, query, cb) -> {
            query.distinct(true);
            return cb.equal(root.join("attendees", JoinType.INNER).get("id"), userId);
        };
    }

    private static <T> void replace(Set<T> target, Set<T> values) {
        target.clear();
        target.addAll(values);
    }

    private static Set<UUID> minus(Set<UUID> a, Set<UUID> b, UUID actorId) {
        Set<UUID> result = new HashSet<>(a);
        result.removeAll(b);
        result.remove(actorId);
        return result;
    }

    private static Set<UUID> intersect(Set<UUID> a, Set<UUID> b, UUID actorId) {
        Set<UUID> result = new HashSet<>(a);
        result.retainAll(b);
        result.remove(actorId);
        return result;
    }
}
