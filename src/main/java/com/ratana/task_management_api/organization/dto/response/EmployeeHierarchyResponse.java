package com.ratana.task_management_api.organization.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Schema(description = "Management chain above a user and their full downline below")
@Builder
@Data
public class EmployeeHierarchyResponse {

    private UUID id;
    private String fullName;

    @Schema(description = "Depth below the top-level employee; 0 = top-level")
    private int level;

    @Schema(description = "Managers ordered nearest-first: direct manager first, top-level employee last")
    private List<OrgMemberResponse> managementChain;

    @Schema(description = "Every user in the downline, breadth-first; level 1 = direct reports")
    private List<OrgMemberResponse> subordinates;
}
