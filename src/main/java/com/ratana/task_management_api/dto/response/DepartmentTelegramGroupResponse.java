package com.ratana.task_management_api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Department Telegram group mapping details")
@Builder
@Data
public class DepartmentTelegramGroupResponse {

    private UUID id;
    private UUID departmentId;
    private String departmentName;
    private String chatId;
    private String groupName;
    private boolean active;
    private Instant verifiedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
