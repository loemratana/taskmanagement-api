package com.ratana.task_management_api.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PermissionResponse {

    private UUID id;
    private String name;
    private String module;
    private String action;
    private String description;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
