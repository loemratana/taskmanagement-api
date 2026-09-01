package com.ratana.task_management_api.dto.request;

import com.ratana.task_management_api.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Schema(description = "Payload to create or update a department")
@Data
public class DepartmentRequest {

    @Schema(description = "Department name", example = "Engineering")
    @NotBlank
    @Size(max = 150)
    private String name;

    @Schema(description = "Optional description", example = "Handles all product engineering")
    private String description;

    @Schema(
            description = "User ID of the department manager",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    private UUID managerId;
}
