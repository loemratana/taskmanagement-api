package com.ratana.task_management_api.organization.dto.response;

import com.ratana.task_management_api.common.response.Summary;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Schema(description = "Minimal user info for department member pickers (e.g. task assignee selection)")
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DepartmentMemberResponse {

    private UUID id;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String avatarUrl;
    private Summary position;
}
