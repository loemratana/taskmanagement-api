package com.ratana.task_management_api.booking.dto.request;

import com.ratana.task_management_api.booking.enums.BookingStatus;
import com.ratana.task_management_api.booking.enums.MeetingLocationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/** All filters optional, combined with AND. Dates are inclusive, in the business time zone. */
@Data
public class BookingFilterRequest {

    private MeetingLocationType locationType;

    private UUID roomId;

    private UUID organizerId;

    @Schema(example = "2026-11-01")
    private LocalDate from;

    @Schema(example = "2026-11-30")
    private LocalDate to;

    private BookingStatus status;
}
