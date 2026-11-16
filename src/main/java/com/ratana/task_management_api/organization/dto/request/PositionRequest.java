package com.ratana.task_management_api.organization.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Schema(description = "Payload to create or update a position")
@AllArgsConstructor
@NoArgsConstructor
public class PositionRequest {


    @Schema(description = "Position title", example = "Backend Developer")
    @NotBlank
    @Size(max = 150)
    private String title;

    @Schema(description = "Short unique code", example = "BE-DEV")
    @Size(max = 20)
    private String code;

    @Schema(description = "Optional description", example = "Owns backend services and APIs")
    private String description;

    @Schema(
            description = "Department this position belongs to",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    @NotNull
    private UUID departmentId;

}
