package com.ratana.task_management_api.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank
        @Email
        @Schema(example = "employee@example.com")
        String email,

        @NotBlank
        @Pattern(regexp = "[0-9]{6}", message = "Code must be 6 digits")
        @Schema(example = "123456")
        String code,

        @NotBlank
        @Size(min = 8, max = 128, message = "New password must be 8-128 characters")
        String newPassword
) {
}
