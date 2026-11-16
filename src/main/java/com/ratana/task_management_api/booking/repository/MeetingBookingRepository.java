package com.ratana.task_management_api.booking.repository;

import com.ratana.task_management_api.booking.entity.MeetingBooking;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Overlap queries use half-open ranges: two bookings clash when
 * {@code a.start < b.end AND a.end > b.start}, so back-to-back slots never clash.
 * {@code excludeId} skips the booking being edited; pass {@link #NO_BOOKING} when creating.
 */
public interface MeetingBookingRepository extends JpaRepository<MeetingBooking, UUID>,
        JpaSpecificationExecutor<MeetingBooking> {

    /** Placeholder for "exclude nothing" (avoids untyped null parameters in Postgres). */
    UUID NO_BOOKING = new UUID(0L, 0L);

    @EntityGraph(attributePaths = {"room", "organizer"})
    @Query("SELECT b FROM MeetingBooking b WHERE b.id = :id")
    Optional<MeetingBooking> findWithDetailsById(@Param("id") UUID id);

    @Query("""
            SELECT b FROM MeetingBooking b
            WHERE b.room.id = :roomId AND b.status = com.ratana.task_management_api.booking.enums.BookingStatus.CONFIRMED
              AND b.startAt < :endAt AND b.endAt > :startAt AND b.id <> :excludeId
            ORDER BY b.startAt
            """)
    List<MeetingBooking> findRoomConflicts(@Param("roomId") UUID roomId,
                                           @Param("startAt") Instant startAt,
                                           @Param("endAt") Instant endAt,
                                           @Param("excludeId") UUID excludeId);

    /** Confirmed bookings of a room touching [from, to), e.g. one day's schedule. */
    @EntityGraph(attributePaths = {"organizer"})
    @Query("""
            SELECT b FROM MeetingBooking b
            WHERE b.room.id = :roomId AND b.status = com.ratana.task_management_api.booking.enums.BookingStatus.CONFIRMED
              AND b.startAt < :to AND b.endAt > :from
            ORDER BY b.startAt
            """)
    List<MeetingBooking> findRoomSchedule(@Param("roomId") UUID roomId,
                                          @Param("from") Instant from,
                                          @Param("to") Instant to);

    /** Confirmed bookings of several rooms touching [from, to), for the multi-room calendar. */
    @EntityGraph(attributePaths = {"organizer"})
    @Query("""
            SELECT b FROM MeetingBooking b
            WHERE b.room.id IN :roomIds AND b.status = com.ratana.task_management_api.booking.enums.BookingStatus.CONFIRMED
              AND b.startAt < :to AND b.endAt > :from
            ORDER BY b.startAt
            """)
    List<MeetingBooking> findRoomsSchedule(@Param("roomIds") Collection<UUID> roomIds,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to);

    @Query("""
            SELECT DISTINCT b.room.id FROM MeetingBooking b
            WHERE b.status = com.ratana.task_management_api.booking.enums.BookingStatus.CONFIRMED
              AND b.startAt < :endAt AND b.endAt > :startAt AND b.id <> :excludeId
            """)
    List<UUID> findBusyRoomIds(@Param("startAt") Instant startAt,
                               @Param("endAt") Instant endAt,
                               @Param("excludeId") UUID excludeId);

    /** How many of {@code userIds} already attend another confirmed meeting in the window. */
    @Query("""
            SELECT COUNT(DISTINCT a.id) FROM MeetingBooking b JOIN b.attendees a
            WHERE a.id IN :userIds AND b.status = com.ratana.task_management_api.booking.enums.BookingStatus.CONFIRMED
              AND b.startAt < :endAt AND b.endAt > :startAt AND b.id <> :excludeId
            """)
    long countAttendeesWithConflicts(@Param("userIds") Collection<UUID> userIds,
                                     @Param("startAt") Instant startAt,
                                     @Param("endAt") Instant endAt,
                                     @Param("excludeId") UUID excludeId);

    /** Future confirmed bookings of a room; used to warn when a room's capacity or status changes. */
    @Query("""
            SELECT b FROM MeetingBooking b
            WHERE b.room.id = :roomId AND b.status = com.ratana.task_management_api.booking.enums.BookingStatus.CONFIRMED
              AND b.endAt > :now
            ORDER BY b.startAt
            """)
    List<MeetingBooking> findFutureConfirmedByRoom(@Param("roomId") UUID roomId, @Param("now") Instant now);

    boolean existsByRoomId(UUID roomId);

    /** Confirmed bookings of a room whose check-in window [start - opens, end] contains now. */
    @Query("""
            SELECT b FROM MeetingBooking b
            WHERE b.room.id = :roomId AND b.status = com.ratana.task_management_api.booking.enums.BookingStatus.CONFIRMED
              AND b.startAt <= :opensBy AND b.endAt >= :now
            ORDER BY b.startAt
            """)
    List<MeetingBooking> findCheckInOpenInRoom(@Param("roomId") UUID roomId,
                                               @Param("now") Instant now,
                                               @Param("opensBy") Instant opensBy);

    /** The next confirmed booking of a room starting after `after`. */
    @Query("""
            SELECT b FROM MeetingBooking b
            WHERE b.room.id = :roomId AND b.status = com.ratana.task_management_api.booking.enums.BookingStatus.CONFIRMED
              AND b.startAt > :after
            ORDER BY b.startAt
            LIMIT 1
            """)
    Optional<MeetingBooking> findNextInRoom(@Param("roomId") UUID roomId, @Param("after") Instant after);

    /** Confirmed bookings that ended before `cutoff` and whose absentees were not written yet. */
    @Query("""
            SELECT b.id FROM MeetingBooking b
            WHERE b.status = com.ratana.task_management_api.booking.enums.BookingStatus.CONFIRMED
              AND b.endAt < :cutoff AND b.attendanceFinalizedAt IS NULL
            ORDER BY b.endAt
            LIMIT 100
            """)
    List<UUID> findIdsToFinalizeAttendance(@Param("cutoff") Instant cutoff);
}
