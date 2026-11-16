package com.ratana.task_management_api.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CheckInRequest {

    @NotBlank
    @Size(max = 200)
    @Schema(description = "Exactly what the QR scanner read", example = "tms-checkin:v1:3f2a…:Ab12…")
    private String code;
}
