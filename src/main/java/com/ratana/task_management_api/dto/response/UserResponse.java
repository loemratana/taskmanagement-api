package com.ratana.task_management_api.dto.response;


import com.ratana.task_management_api.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "User details")
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    UUID id;
    String email;
    String fullName;
    String avatarUrl;
    UserStatus status;
    boolean active;
    Instant lastLoginAt;
    Instant createdAt;
    Summary position;
    Summary department;
    Summary manager;
    Summary role;



}
