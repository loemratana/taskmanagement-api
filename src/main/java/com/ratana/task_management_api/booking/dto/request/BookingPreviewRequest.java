package com.ratana.task_management_api.booking.dto.request;

import com.ratana.task_management_api.booking.enums.MeetingLocationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Check a booking before saving it. Only targets are required; add the room to check
 * capacity, and date/times to check attendee clashes and get suggested free rooms.
 */
@Data
public class BookingPreviewRequest {

    @Schema(description = "Defaults to ROOM. Room checks and room suggestions only run for ROOM.")
    private MeetingLocationType locationType;

    private UUID roomId;

    private LocalDate date;

    @Schema(type = "string", example = "15:00")
    private LocalTime startTime;

    @Schema(type = "string", example = "16:00")
    private LocalTime endTime;

    @Schema(description = "When previewing an edit, the booking being edited (ignored in clash checks)")
    private UUID excludeBookingId;

    @Valid
    private BookingTargetsRequest targets = new BookingTargetsRequest();
}
