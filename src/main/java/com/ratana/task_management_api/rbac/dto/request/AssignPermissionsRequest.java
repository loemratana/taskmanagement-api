package com.ratana.task_management_api.rbac.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Schema(description = "Set of permission IDs to assign to a role — replaces the existing set entirely")
@Data
public class AssignPermissionsRequest {

    private Set<UUID> permissionIds;
}
