package com.ratana.task_management_api.user.dto.response;

import com.ratana.task_management_api.common.response.Summary;


import com.ratana.task_management_api.user.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;
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
    String phoneNumber;
    String avatarUrl;
    UserStatus status;
    boolean active;
    Instant lastLoginAt;
    Instant createdAt;
    Summary position;
    Summary department;
    Summary manager;
    Set<Summary> roles;

    /** Effective permission names (e.g. "tasks:assign:team"). Only filled by GET /users/me. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Set<String> permissions;
}
