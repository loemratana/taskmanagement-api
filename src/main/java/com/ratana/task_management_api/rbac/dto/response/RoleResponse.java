package com.ratana.task_management_api.rbac.dto.response;


import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@Getter
@Setter
public class RoleResponse {

    private UUID id;
    private String name;
    private String description;
    private boolean active;
    List<PermissionSummary> permissions;
    private Instant created;
    private Instant updated;

}
