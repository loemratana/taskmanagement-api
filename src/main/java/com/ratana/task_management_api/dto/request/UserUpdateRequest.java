package com.ratana.task_management_api.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Schema(description = "Payload to create a new user")
@AllArgsConstructor
@Data
@NoArgsConstructor
public class UserUpdateRequest {



    @Email @Size(max = 255)
    private String email;
    @Size(max = 200)
    private String fullName;
    private String avartarUrl;
    private UUID positionId;
    private UUID managerId;

}
