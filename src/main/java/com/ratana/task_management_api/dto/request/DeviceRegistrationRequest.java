package com.ratana.task_management_api.dto.request;

import com.ratana.task_management_api.enums.Platform;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
@Valid
public class DeviceRegistrationRequest {

    @NotBlank
    private String deviceToken;

    @NotNull
    private Platform deviceType;
}
