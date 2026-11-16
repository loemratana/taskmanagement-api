package com.ratana.task_management_api.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MarkAttendanceRequest {

    @NotNull
    @Pattern(regexp = "PRESENT|LATE|ABSENT|EXCUSED|UNKNOWN",
            message = "status must be PRESENT, LATE, ABSENT, EXCUSED or UNKNOWN")
    @Schema(description = "UNKNOWN clears the mark", example = "PRESENT")
    private String status;

    @Size(max = 300)
    @Schema(description = "e.g. the reason for an excused absence")
    private String note;
}
