package com.ratana.task_management_api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.Getter;

public record LoginRequest(
        @NotBlank @Email
        @Schema(example = "admin@example.com")
        String email,

        @NotBlank
        @Schema(example = "password")
        String password
) {
}
