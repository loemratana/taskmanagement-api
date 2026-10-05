package com.ratana.task_management_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Schema(description = "An employee's position within the reporting hierarchy")
@Builder
@Data
public class EmployeeHierarchyResponse {
    UUID id;
    String fullName;
    String avatarUrl;
    /** Depth below the top-level (root) employee; 0 = the employee is top-level. */
    int level;
    /** Ancestors from the immediate manager up to the root. Empty if top-level. */
    List<OrgMemberResponse> managementChain;
    /** Every employee under this one, at any depth, top-down. Empty if a leaf. */
    List<OrgMemberResponse> subordinates;
}
