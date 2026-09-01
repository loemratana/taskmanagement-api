package com.ratana.task_management_api.dto.request;

import jakarta.validation.constraints.NotBlank;
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
    private String newPassword;
}
