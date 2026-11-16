package com.ratana.task_management_api.organization.dto.response;

import com.ratana.task_management_api.user.dto.response.ManagerResponse;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Schema(description = "Department with its positions and the active members holding each one")
@Builder
@Data
public class DepartmentTreeResponse {

    private UUID id;
    private String name;
    private String description;
    private ManagerResponse manager;

    @Schema(description = "Active members in the department, across all positions")
    private int memberCount;

    @Schema(description = "Active positions in the department, plus any other position a member holds; sorted by name")
    private List<PositionNode> positions;

    @Schema(description = "Members of the department with no position")
    private List<DepartmentMemberResponse> unassignedMembers;

    @Builder
    @Data
    public static class PositionNode {
        private UUID id;
        private String name;
        private List<DepartmentMemberResponse> members;
    }
}
