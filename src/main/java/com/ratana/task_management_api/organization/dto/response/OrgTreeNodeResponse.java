package com.ratana.task_management_api.organization.dto.response;

import com.ratana.task_management_api.common.response.Summary;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Schema(description = "Employee node within the reporting tree")
@Builder
@Data
public class OrgTreeNodeResponse {

    private UUID id;
    private String fullName;
    private String email;
    private String avatarUrl;
    private Summary position;
    private Summary department;

    @Schema(description = "Depth below the top-level employee; 0 = top-level")
    private int level;

    private List<OrgTreeNodeResponse> children;
}
