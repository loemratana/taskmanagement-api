package com.ratana.task_management_api.booking.scheduler;

import com.ratana.task_management_api.booking.repository.MeetingBookingRepository;
import com.ratana.task_management_api.booking.service.MeetingAttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Marks invitees ABSENT once a meeting has been over for app.booking.absent-after-end-minutes. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingAttendanceScheduler {

    private final MeetingBookingRepository bookingRepository;
    private final MeetingAttendanceService attendanceService;

    @Scheduled(fixedDelayString = "${app.booking.attendance-check-interval-ms:300000}")
    public void finalizeEndedMeetings() {
        List<UUID> bookingIds = bookingRepository.findIdsToFinalizeAttendance(attendanceService.finalizeCutoff());
        for (UUID bookingId : bookingIds) {
            try {
                int absent = attendanceService.finalizeAttendance(bookingId);
                log.info("Attendance finalized: booking={} markedAbsent={}", bookingId, absent);
            } catch (RuntimeException e) {
                // One bad booking must not stop the others; it is retried on the next run.
                log.warn("Could not finalize attendance for booking {}: {}", bookingId, e.getMessage());
            }
        }
    }
}
