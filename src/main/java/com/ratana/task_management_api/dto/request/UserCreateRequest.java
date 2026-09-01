package com.ratana.task_management_api.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Schema(description = "Payload to create a new user")
@AllArgsConstructor
@Data
@NoArgsConstructor
public class UserCreateRequest {


    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;


    @NotBlank @Size(max = 200)
    private String fullName;

    private UUID positionId;
    private UUID managerId;

    private Set<UUID> role;


    private String avatarUrl;
}
