package com.ratana.task_management_api.rbac.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PermissionSummary {
    private UUID id;
    private String name;
    private String model;
    private String action;
}
