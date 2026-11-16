package com.ratana.task_management_api.booking.dto.request;

import com.ratana.task_management_api.booking.enums.MeetingLocationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Create or fully replace (reschedule/re-target) a booking. Times are business-zone wall clock. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequest {

    @Schema(description = "Defaults to ROOM when omitted", example = "ROOM")
    private MeetingLocationType locationType;

    @Schema(description = "Required when locationType is ROOM; ignored otherwise")
    private UUID roomId;

    @Valid
    @Schema(description = "Required for EXTERNAL (map pin) and ONLINE (meeting link); optional note for ROOM")
    private BookingLocationRequest location;

    @NotBlank
    @Size(max = 200)
    @Schema(example = "Weekly IT sync")
    private String title;

    @Size(max = 5000)
    private String description;

    @NotNull
    @Schema(example = "2026-11-12")
    private LocalDate date;

    @NotNull
    @Schema(type = "string", example = "15:00")
    private LocalTime startTime;

    @NotNull
    @Schema(type = "string", example = "16:00")
    private LocalTime endTime;

    @Valid
    @Builder.Default
    private BookingTargetsRequest targets = new BookingTargetsRequest();
}
