package com.ratana.task_management_api.booking.service;

import com.ratana.task_management_api.file.config.AttachmentUploadPolicy;
import com.ratana.task_management_api.booking.config.BookingProperties;
import com.ratana.task_management_api.booking.dto.request.RoomFilterRequest;
import com.ratana.task_management_api.booking.dto.request.RoomRequest;
import com.ratana.task_management_api.common.pagination.PageResponse;
import com.ratana.task_management_api.booking.dto.response.RoomCalendarResponse;
import com.ratana.task_management_api.booking.dto.response.RoomCalendarResponse.DaySchedule;
import com.ratana.task_management_api.booking.dto.response.RoomCalendarResponse.RoomDays;
import com.ratana.task_management_api.booking.dto.response.RoomResponse;
import com.ratana.task_management_api.booking.dto.response.RoomScheduleResponse;
import com.ratana.task_management_api.booking.dto.response.RoomScheduleResponse.BusySlot;
import com.ratana.task_management_api.booking.dto.response.RoomScheduleResponse.FreeSlot;
import com.ratana.task_management_api.booking.enums.RoomFacility;
import com.ratana.task_management_api.booking.enums.RoomStatus;
import com.ratana.task_management_api.common.exception.BadRequestException;
import com.ratana.task_management_api.common.exception.DuplicateResourceException;
import com.ratana.task_management_api.file.exception.FileSizeExceededException;
import com.ratana.task_management_api.common.exception.InvalidRequestException;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.file.exception.UnsupportedFileTypeException;
import com.ratana.task_management_api.booking.mapper.RoomBookingMapper;
import com.ratana.task_management_api.booking.entity.MeetingBooking;
import com.ratana.task_management_api.booking.entity.Room;
import com.ratana.task_management_api.booking.entity.RoomImage;
import com.ratana.task_management_api.booking.repository.MeetingBookingRepository;
import com.ratana.task_management_api.booking.repository.RoomRepository;
import com.ratana.task_management_api.infrastructure.storage.StorageService;
import com.ratana.task_management_api.booking.service.BookingTimePolicy.TimeWindow;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private static final UUID NO_ROOM = new UUID(0L, 0L);
    private static final String IMAGE_DIRECTORY = "rooms";
    /** Longest range of the multi-room calendar (a month view). */
    static final int MAX_CALENDAR_DAYS = 31;

    private final RoomRepository roomRepository;
    private final MeetingBookingRepository bookingRepository;
    private final StorageService storageService;
    private final RoomBookingMapper mapper;
    private final BookingTimePolicy timePolicy;
    private final BookingProperties properties;

    // ---------------------------------------------------------------- CRUD

    @Override
    @Transactional
    public RoomResponse create(RoomRequest request) {
        ensureUnique(request, NO_ROOM);
        Room room = new Room();
        apply(room, request);
        Room saved = roomRepository.save(room);
        log.info("Room created: id={} name='{}' capacity={}", saved.getId(), saved.getName(), saved.getCapacity());
        return mapper.toRoomResponse(saved);
    }

    @Override
    @Transactional
    public RoomResponse update(UUID id, RoomRequest request) {
        Room room = getRoom(id);
        ensureUnique(request, id);
        apply(room, request);
        Room saved = roomRepository.save(room);
        log.info("Room updated: id={} capacity={} status={}", id, saved.getCapacity(), saved.getStatus());
        return mapper.toRoomResponse(saved).withWarnings(futureBookingWarnings(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public RoomResponse get(UUID id) {
        return mapper.toRoomResponse(getRoom(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RoomResponse> search(RoomFilterRequest filter, Pageable pageable) {
        Pageable page = pageable.getSort().isSorted() ? pageable
                : org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by("name"));
        Page<Room> rooms = roomRepository.findAll(toSpecification(filter), page);
        return PageResponse.from(rooms.map(mapper::toRoomResponse));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Room room = getRoom(id);
        if (bookingRepository.existsByRoomId(id)) {
            throw new BadRequestException("Room '%s' has bookings and cannot be deleted; set its status to INACTIVE instead"
                    .formatted(room.getName()));
        }
        List<String> keys = room.getImages().stream().map(RoomImage::getStorageKey).toList();
        roomRepository.delete(room);
        deleteFromStorageAfterCommit(keys);
        log.info("Room deleted: id={} name='{}'", id, room.getName());
    }

    // ---------------------------------------------------------------- Images

    @Override
    @Transactional
    public RoomResponse uploadImages(UUID roomId, List<MultipartFile> files) {
        Room room = getRoom(roomId);
        List<MultipartFile> uploads = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (uploads.isEmpty()) {
            throw new InvalidRequestException("At least one image file is required");
        }
        int max = properties.maxImagesPerRoom();
        if (room.getImages().size() + uploads.size() > max) {
            throw new BadRequestException("A room can have at most %d images (it has %d, you sent %d)"
                    .formatted(max, room.getImages().size(), uploads.size()));
        }
        uploads.forEach(this::validateImage);

        List<String> uploadedKeys = new ArrayList<>();
        deleteFromStorageOnRollback(uploadedKeys);
        boolean hasCover = room.getImages().stream().anyMatch(RoomImage::isCover);
        int nextOrder = room.getImages().stream().mapToInt(RoomImage::getSortOrder).max().orElse(-1) + 1;

        for (MultipartFile file : uploads) {
            String key = storageService.upload(file, IMAGE_DIRECTORY + "/" + roomId).getStorageKey();
            uploadedKeys.add(key);

            RoomImage image = new RoomImage();
            image.setRoom(room);
            image.setStorageKey(key);
            image.setContentType(AttachmentUploadPolicy.detectContentType(file));
            image.setSizeBytes(file.getSize());
            image.setSortOrder(nextOrder++);
            image.setCover(!hasCover);
            hasCover = true;
            room.getImages().add(image);
        }
        Room saved = roomRepository.saveAndFlush(room);
        log.info("Uploaded {} image(s) to room {}", uploadedKeys.size(), roomId);
        return mapper.toRoomResponse(saved);
    }

    @Override
    @Transactional
    public RoomResponse setCoverImage(UUID roomId, UUID imageId) {
        Room room = getRoom(roomId);
        RoomImage target = findImage(room, imageId);
        // Clear the old cover first: the DB allows only one cover per room at any moment.
        room.getImages().forEach(image -> image.setCover(false));
        roomRepository.saveAndFlush(room);
        target.setCover(true);
        return mapper.toRoomResponse(roomRepository.saveAndFlush(room));
    }

    @Override
    @Transactional
    public RoomResponse reorderImages(UUID roomId, List<UUID> imageIds) {
        Room room = getRoom(roomId);
        Set<UUID> current = new HashSet<>();
        room.getImages().forEach(image -> current.add(image.getId()));
        if (imageIds.size() != current.size() || !current.equals(new HashSet<>(imageIds))) {
            throw new BadRequestException("imageIds must list every image of the room exactly once");
        }
        for (int i = 0; i < imageIds.size(); i++) {
            findImage(room, imageIds.get(i)).setSortOrder(i);
        }
        room.getImages().sort((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()));
        return mapper.toRoomResponse(roomRepository.saveAndFlush(room));
    }

    @Override
    @Transactional
    public RoomResponse deleteImage(UUID roomId, UUID imageId) {
        Room room = getRoom(roomId);
        RoomImage image = findImage(room, imageId);
        room.getImages().remove(image);
        roomRepository.saveAndFlush(room);
        if (image.isCover() && !room.getImages().isEmpty()) {
            room.getImages().get(0).setCover(true);
            roomRepository.saveAndFlush(room);
        }
        deleteFromStorageAfterCommit(List.of(image.getStorageKey()));
        log.info("Deleted image {} from room {}", imageId, roomId);
        return mapper.toRoomResponse(room);
    }

    // ---------------------------------------------------------------- Availability

    @Override
    @Transactional(readOnly = true)
    public RoomScheduleResponse schedule(UUID roomId, LocalDate date) {
        Room room = getRoom(roomId);
        List<MeetingBooking> bookings = bookingRepository.findRoomSchedule(
                roomId, timePolicy.startOfDay(date), timePolicy.endOfDay(date));
        DaySchedule day = daySchedule(date, bookings);
        return new RoomScheduleResponse(room.getId(), room.getName(), room.getCapacity(), date,
                timePolicy.workdayStart(), timePolicy.workdayEnd(), day.busy(), day.free());
    }

    @Override
    @Transactional(readOnly = true)
    public RoomCalendarResponse calendar(LocalDate from, LocalDate to, UUID roomId,
                                         Integer minCapacity, RoomFacility facility) {
        if (from == null) {
            throw new BadRequestException("from is required");
        }
        LocalDate until = to != null ? to : from;
        if (until.isBefore(from)) {
            throw new BadRequestException("to must be on or after from");
        }
        if (ChronoUnit.DAYS.between(from, until) + 1 > MAX_CALENDAR_DAYS) {
            throw new BadRequestException("The schedule covers at most %d days at a time".formatted(MAX_CALENDAR_DAYS));
        }

        List<Room> rooms;
        if (roomId != null) {
            rooms = List.of(getRoom(roomId));
        } else {
            RoomFilterRequest filter = new RoomFilterRequest();
            filter.setStatus(RoomStatus.ACTIVE);
            filter.setMinCapacity(minCapacity);
            filter.setFacility(facility);
            rooms = roomRepository.findAll(toSpecification(filter), Sort.by("name"));
        }

        Map<UUID, List<MeetingBooking>> bookingsByRoom = rooms.isEmpty() ? Map.of()
                : bookingRepository.findRoomsSchedule(rooms.stream().map(Room::getId).toList(),
                                timePolicy.startOfDay(from), timePolicy.endOfDay(until))
                        .stream()
                        .collect(Collectors.groupingBy(booking -> booking.getRoom().getId()));

        List<LocalDate> dates = from.datesUntil(until.plusDays(1)).toList();
        List<RoomDays> result = rooms.stream()
                .map(room -> {
                    List<MeetingBooking> roomBookings = bookingsByRoom.getOrDefault(room.getId(), List.of());
                    List<DaySchedule> days = dates.stream().map(date -> daySchedule(date, roomBookings)).toList();
                    return new RoomDays(mapper.toRoomSummary(room), room.getStatus(), days);
                })
                .toList();
        return new RoomCalendarResponse(from, until, timePolicy.workdayStart(), timePolicy.workdayEnd(), result);
    }

    /**
     * One day of a room: {@code bookings} (sorted by start, may include other days) cut to the day,
     * plus the free gaps between them within the workday.
     */
    private DaySchedule daySchedule(LocalDate date, List<MeetingBooking> bookings) {
        Instant dayStartAt = timePolicy.startOfDay(date);
        Instant dayEndAt = timePolicy.endOfDay(date);
        List<BusySlot> busy = new ArrayList<>();
        List<FreeSlot> free = new ArrayList<>();
        LocalTime workdayEnd = timePolicy.workdayEnd();
        LocalTime cursor = timePolicy.workdayStart();

        for (MeetingBooking booking : bookings) {
            if (!booking.getStartAt().isBefore(dayEndAt) || !booking.getEndAt().isAfter(dayStartAt)) {
                continue;
            }
            // A meeting crossing midnight is shown up to the end of this day / from its start.
            LocalTime start = booking.getStartAt().isAfter(dayStartAt)
                    ? timePolicy.localTime(booking.getStartAt()) : LocalTime.MIDNIGHT;
            LocalTime end = booking.getEndAt().isBefore(dayEndAt)
                    ? timePolicy.localTime(booking.getEndAt()) : LocalTime.MAX;
            busy.add(new BusySlot(booking.getId(), booking.getTitle(), booking.getOrganizer().getFullName(), start, end));
            if (start.isAfter(cursor) && cursor.isBefore(workdayEnd)) {
                free.add(new FreeSlot(cursor, start.isBefore(workdayEnd) ? start : workdayEnd));
            }
            if (end.isAfter(cursor)) {
                cursor = end;
            }
        }
        if (cursor.isBefore(workdayEnd)) {
            free.add(new FreeSlot(cursor, workdayEnd));
        }
        return new DaySchedule(date, busy, free);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> findAvailable(LocalDate date, LocalTime startTime, LocalTime endTime,
                                            Integer minCapacity, RoomFacility facility) {
        TimeWindow window = timePolicy.toWindow(date, startTime, endTime);
        Set<UUID> busy = new HashSet<>(bookingRepository.findBusyRoomIds(
                window.startAt(), window.endAt(), MeetingBookingRepository.NO_BOOKING));

        RoomFilterRequest filter = new RoomFilterRequest();
        filter.setStatus(RoomStatus.ACTIVE);
        filter.setMinCapacity(minCapacity);
        filter.setFacility(facility);
        return roomRepository.findAll(toSpecification(filter), Sort.by("capacity", "name")).stream()
                .filter(room -> !busy.contains(room.getId()))
                .map(mapper::toRoomResponse)
                .toList();
    }

    // ---------------------------------------------------------------- Helpers

    private Room getRoom(UUID id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + id));
    }

    private static RoomImage findImage(Room room, UUID imageId) {
        return room.getImages().stream()
                .filter(image -> image.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Image %s not found in room %s"
                        .formatted(imageId, room.getId())));
    }

    private void apply(Room room, RoomRequest request) {
        room.setName(request.getName().trim());
        room.setCode(StringUtils.hasText(request.getCode()) ? request.getCode().trim().toUpperCase() : null);
        room.setLocation(StringUtils.hasText(request.getLocation()) ? request.getLocation().trim() : null);
        room.setDescription(request.getDescription());
        room.setCapacity(request.getCapacity());
        room.setStatus(request.getStatus() != null ? request.getStatus() : RoomStatus.ACTIVE);
        room.getFacilities().clear();
        if (request.getFacilities() != null) {
            room.getFacilities().addAll(request.getFacilities());
        }
    }

    private void ensureUnique(RoomRequest request, UUID excludeId) {
        if (roomRepository.existsByNameIgnoreCase(request.getName().trim(), excludeId)) {
            throw new DuplicateResourceException("A room named '%s' already exists".formatted(request.getName().trim()));
        }
        if (StringUtils.hasText(request.getCode())
                && roomRepository.existsByCodeIgnoreCase(request.getCode().trim(), excludeId)) {
            throw new DuplicateResourceException("A room with code '%s' already exists".formatted(request.getCode().trim()));
        }
    }

    /** Future bookings that no longer fit: too many attendees, or the room is no longer bookable. */
    private List<String> futureBookingWarnings(Room room) {
        List<MeetingBooking> future = bookingRepository.findFutureConfirmedByRoom(room.getId(), timePolicy.now());
        List<String> warnings = new ArrayList<>();
        for (MeetingBooking booking : future) {
            String when = "%s %s-%s".formatted(timePolicy.localDate(booking.getStartAt()),
                    timePolicy.localTime(booking.getStartAt()), timePolicy.localTime(booking.getEndAt()));
            if (!room.isBookable()) {
                warnings.add("Booking '%s' on %s is still scheduled in this %s room"
                        .formatted(booking.getTitle(), when, room.getStatus().name().toLowerCase()));
            } else if (booking.getAttendeeCount() > room.getCapacity()) {
                warnings.add("Booking '%s' on %s has %d attendees, above the new capacity of %d"
                        .formatted(booking.getTitle(), when, booking.getAttendeeCount(), room.getCapacity()));
            }
        }
        return warnings;
    }

    private void validateImage(MultipartFile file) {
        if (file.getSize() > properties.maxImageSizeBytes()) {
            throw new FileSizeExceededException("Image '%s' is larger than %d MB"
                    .formatted(file.getOriginalFilename(), properties.maxImageSizeBytes() / (1024 * 1024)));
        }
        if (!AttachmentUploadPolicy.isImage(file)) {
            throw new UnsupportedFileTypeException("Only image files can be uploaded, got: " + file.getContentType());
        }
    }

    private static Specification<Room> toSpecification(RoomFilterRequest filter) {
        Specification<Room> spec = (root, query, cb) -> cb.conjunction();
        if (filter == null) {
            return spec;
        }
        if (StringUtils.hasText(filter.getSearch())) {
            String like = "%" + filter.getSearch().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("code"), "")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("location"), "")), like)));
        }
        if (filter.getMinCapacity() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("capacity"), filter.getMinCapacity()));
        }
        if (filter.getStatus() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), filter.getStatus()));
        }
        if (filter.getFacility() != null) {
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                return cb.equal(root.join("facilities", JoinType.INNER), filter.getFacility());
            });
        }
        return spec;
    }

    private void deleteFromStorageAfterCommit(List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        Runnable delete = () -> keys.forEach(this::deleteQuietly);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delete.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delete.run();
            }
        });
    }

    /** Uploaded objects whose DB rows never got committed would otherwise be orphaned. */
    private void deleteFromStorageOnRollback(List<String> keys) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    keys.forEach(RoomServiceImpl.this::deleteQuietly);
                }
            }
        });
    }

    private void deleteQuietly(String key) {
        try {
            storageService.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete room image {} from storage: {}", key, e.getMessage());
        }
    }
}
