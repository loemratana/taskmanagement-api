package com.ratana.task_management_api.user.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    @Pattern(regexp = "^\\+?[0-9 ()-]{6,20}$", message = "Invalid phone number")
    private String phoneNumber;
    private String avartarUrl;
    private UUID positionId;
    private UUID managerId;

}
