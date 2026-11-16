package com.ratana.task_management_api.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {


    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(min = 8, max = 128, message = "New password must be 8-128 characters")
    private String newPassword;
}
