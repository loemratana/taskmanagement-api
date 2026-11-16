package com.ratana.task_management_api.booking.repository;

import com.ratana.task_management_api.booking.entity.MeetingAttendance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingAttendanceRepository extends JpaRepository<MeetingAttendance, UUID> {

    @EntityGraph(attributePaths = {"markedBy"})
    @Query("SELECT a FROM MeetingAttendance a WHERE a.booking.id = :bookingId")
    List<MeetingAttendance> findByBookingId(@Param("bookingId") UUID bookingId);

    @Query("SELECT a FROM MeetingAttendance a WHERE a.booking.id = :bookingId AND a.user.id = :userId")
    Optional<MeetingAttendance> findByBookingIdAndUserId(@Param("bookingId") UUID bookingId,
                                                          @Param("userId") UUID userId);
}
