package com.ratana.task_management_api.organization.dto.response;

import com.ratana.task_management_api.user.dto.response.ManagerResponse;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Department details, flat form")
@Builder
@Data
public class DepartmentResponse {

    private UUID id;
    private String name;
    private String description;

    private ManagerResponse manager ;
    private String avatar;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
