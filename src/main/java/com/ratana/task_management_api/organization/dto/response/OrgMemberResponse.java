package com.ratana.task_management_api.organization.dto.response;

import com.ratana.task_management_api.common.response.Summary;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Schema(description = "A user's position in the reporting hierarchy, relative to the user it was resolved for")
@Builder
@Data
public class OrgMemberResponse {

    private UUID id;
    private String fullName;
    private String email;
    private String avatarUrl;
    private Summary position;
    private Summary department;
    private UUID managerId;

    @Schema(description = "Distance from the subject user: 1 = direct manager / direct report")
    private int level;
}
