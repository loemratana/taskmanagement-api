package com.ratana.task_management_api.booking.mapper;

import com.ratana.task_management_api.booking.dto.response.BookingLocationResponse;
import com.ratana.task_management_api.booking.dto.response.BookingResponse;
import com.ratana.task_management_api.booking.entity.MeetingLocation;
import com.ratana.task_management_api.booking.dto.response.BookingResponse.PersonRef;
import com.ratana.task_management_api.booking.dto.response.BookingResponse.PositionRef;
import com.ratana.task_management_api.booking.dto.response.BookingResponse.Ref;
import com.ratana.task_management_api.booking.dto.response.RoomResponse;
import com.ratana.task_management_api.booking.dto.response.RoomSummaryResponse;
import com.ratana.task_management_api.booking.enums.RoomFacility;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.booking.entity.MeetingBooking;
import com.ratana.task_management_api.organization.entity.Position;
import com.ratana.task_management_api.booking.entity.Room;
import com.ratana.task_management_api.booking.entity.RoomImage;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.infrastructure.storage.StorageService;
import com.ratana.task_management_api.booking.service.BookingTimePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RoomBookingMapper {

    private final StorageService storageService;
    private final BookingTimePolicy timePolicy;

    // ---------------------------------------------------------------- Rooms

    public RoomResponse toRoomResponse(Room room) {
        List<RoomResponse.RoomImageResponse> images = room.getImages().stream()
                .map(this::toImageResponse)
                .toList();
        return RoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .code(room.getCode())
                .location(room.getLocation())
                .description(room.getDescription())
                .capacity(room.getCapacity())
                .status(room.getStatus())
                .facilities(room.getFacilities().isEmpty() ? EnumSet.noneOf(RoomFacility.class)
                        : EnumSet.copyOf(room.getFacilities()))
                .coverImageUrl(coverUrl(room))
                .images(images)
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .build();
    }

    public RoomSummaryResponse toRoomSummary(Room room) {
        return new RoomSummaryResponse(room.getId(), room.getName(), room.getCode(), room.getLocation(),
                room.getCapacity(), coverUrl(room));
    }

    private RoomResponse.RoomImageResponse toImageResponse(RoomImage image) {
        return new RoomResponse.RoomImageResponse(image.getId(), storageService.publicUrl(image.getStorageKey()),
                image.getContentType(), image.getSizeBytes(), image.getSortOrder(), image.isCover());
    }

    /** The cover image, or the first image when none is marked. */
    private String coverUrl(Room room) {
        return room.getImages().stream()
                .filter(RoomImage::isCover)
                .findFirst()
                .or(() -> room.getImages().stream().findFirst())
                .map(image -> storageService.publicUrl(image.getStorageKey()))
                .orElse(null);
    }

    // ---------------------------------------------------------------- Bookings

    /**
     * @param withAttendees include the full attendee list (detail view)
     * @param canEdit       whether the current user may edit/cancel it now
     */
    public BookingResponse toBookingResponse(MeetingBooking booking, boolean withAttendees, boolean canEdit) {
        return BookingResponse.builder()
                .id(booking.getId())
                .title(booking.getTitle())
                .description(booking.getDescription())
                .locationType(booking.getLocationType())
                .room(booking.getRoom() != null ? toRoomSummary(booking.getRoom()) : null)
                .location(toLocation(booking))
                .organizer(toPerson(booking.getOrganizer()))
                .date(timePolicy.localDate(booking.getStartAt()))
                .startTime(timePolicy.localTime(booking.getStartAt()))
                .endTime(timePolicy.localTime(booking.getEndAt()))
                .startAt(booking.getStartAt())
                .endAt(booking.getEndAt())
                .durationMinutes(Duration.between(booking.getStartAt(), booking.getEndAt()).toMinutes())
                .status(booking.getStatus())
                .timeStatus(timePolicy.timeStatus(booking))
                .attendeeCount(booking.getAttendeeCount())
                .targets(new BookingResponse.Targets(
                        sorted(booking.getTargetDepartments().stream().map(this::toRef).toList(),
                                Comparator.comparing(Ref::name)),
                        sorted(booking.getTargetPositions().stream().map(this::toPositionRef).toList(),
                                Comparator.comparing(PositionRef::name)),
                        people(booking.getTargetUsers())))
                .attendees(withAttendees ? people(booking.getAttendees()) : null)
                .canEdit(canEdit)
                .cancelledAt(booking.getCancelledAt())
                .cancelledBy(booking.getCancelledBy() != null ? toPerson(booking.getCancelledBy()) : null)
                .cancelReason(booking.getCancelReason())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }

    /** Always present, so clients can show {@code label} without checking the type first. */
    public BookingLocationResponse toLocation(MeetingBooking booking) {
        MeetingLocation location = booking.getLocation() != null ? booking.getLocation() : new MeetingLocation();
        return new BookingLocationResponse(booking.getLocationType(), booking.locationLabel(),
                location.getAddress(), location.getLatitude(), location.getLongitude(), location.getGooglePlaceId(),
                location.mapUrl(), location.getMeetingUrl(), location.getNote());
    }

    public Ref toRef(Department department) {
        return new Ref(department.getId(), department.getName());
    }

    private PositionRef toPositionRef(Position position) {
        Department department = position.getDepartment();
        return new PositionRef(position.getId(), position.getName(),
                department != null ? department.getId() : null,
                department != null ? department.getName() : null);
    }

    public PersonRef toPerson(User user) {
        return new PersonRef(user.getId(), user.getFullName(), user.getAvatarUrl(),
                user.getDepartment() != null ? user.getDepartment().getName() : null,
                user.getPhoneNumber());
    }

    private List<PersonRef> people(Collection<User> users) {
        return sorted(users.stream().map(this::toPerson).toList(), Comparator.comparing(PersonRef::fullName));
    }

    private static <T> List<T> sorted(List<T> list, Comparator<T> comparator) {
        return list.stream().sorted(comparator).toList();
    }
}
