package com.ratana.task_management_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Schema(description = "One node in the organization tree, rooted at a top-level employee")
@Builder
@Data
public class OrganizationTreeNodeResponse {
    UUID id;
    String fullName;
    String email;
    String avatarUrl;
    Summary position;
    Summary department;
    int level;
    @Builder.Default
    List<OrganizationTreeNodeResponse> children = new ArrayList<>();
}
