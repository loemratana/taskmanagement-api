package com.ratana.task_management_api.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank
        @Email
        @Schema(example = "admin@example.com")
        String email,

        @NotBlank
        @Schema(example = "Admin@123")
        String password
) {
}
