package com.ratana.task_management_api.booking.entity;

import com.ratana.task_management_api.common.entity.BaseEntity;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.organization.entity.Position;
import com.ratana.task_management_api.user.entity.User;

import com.ratana.task_management_api.booking.enums.BookingStatus;
import com.ratana.task_management_api.booking.enums.MeetingLocationType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "meeting_bookings")
@Getter
@Setter
@NoArgsConstructor
public class MeetingBooking extends BaseEntity {

    @Version
    private Long version = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", length = 20, nullable = false)
    private MeetingLocationType locationType = MeetingLocationType.ROOM;

    /** Set only when {@link #locationType} is ROOM. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    /** Place or call details; null when nothing beyond the room was given. */
    @Embedded
    private MeetingLocation location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private User organizer;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    /** Exclusive: a 15:00-16:00 booking does not clash with one starting at 16:00. */
    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private BookingStatus status = BookingStatus.CONFIRMED;

    /** Size of {@link #attendees} when last saved (organizer included). */
    @Column(name = "attendee_count", nullable = false)
    private int attendeeCount;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_id")
    private User cancelledBy;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    /** When the post-meeting job marked everyone without a check-in as absent. */
    @Column(name = "attendance_finalized_at")
    private Instant attendanceFinalizedAt;

    // ---- What the organizer selected ----

    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(name = "booking_target_departments",
            joinColumns = @JoinColumn(name = "booking_id"),
            inverseJoinColumns = @JoinColumn(name = "department_id"))
    private Set<Department> targetDepartments = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(name = "booking_target_positions",
            joinColumns = @JoinColumn(name = "booking_id"),
            inverseJoinColumns = @JoinColumn(name = "position_id"))
    private Set<Position> targetPositions = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(name = "booking_target_users",
            joinColumns = @JoinColumn(name = "booking_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> targetUsers = new HashSet<>();

    // ---- Resolved snapshot ----

    /** Targets resolved to active users when saved, organizer included. */
    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(name = "booking_attendees",
            joinColumns = @JoinColumn(name = "booking_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> attendees = new HashSet<>();

    public boolean isConfirmed() {
        return status == BookingStatus.CONFIRMED;
    }

    public boolean isInRoom() {
        return locationType == MeetingLocationType.ROOM;
    }

    /** Short display name of where the meeting is: the room, the place, or "Online meeting". */
    public String locationLabel() {
        return switch (locationType) {
            case ROOM -> room != null ? room.getName() : null;
            case EXTERNAL -> location != null ? location.getName() : null;
            case ONLINE -> location != null && location.getName() != null ? location.getName() : "Online meeting";
        };
    }
}
