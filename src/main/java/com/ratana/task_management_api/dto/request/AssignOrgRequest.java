package com.ratana.task_management_api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignOrgRequest {

    /** Leave null to keep the user's current department. */
    private UUID departmentId;

    /** Leave null to keep the user's current position. */
    private UUID positionId;
}
