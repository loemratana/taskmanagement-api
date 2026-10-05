package com.ratana.task_management_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Schema(description = "One employee within a management chain or subordinate listing")
@Builder
@Data
public class OrgMemberResponse {
    UUID id;
    String fullName;
    String email;
    String avatarUrl;
    Summary position;
    Summary department;
    UUID managerId;
    /** Distance from the employee the query was anchored on. */
    int level;
}
