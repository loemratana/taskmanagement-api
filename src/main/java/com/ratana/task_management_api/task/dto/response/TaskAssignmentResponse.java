package com.ratana.task_management_api.task.dto.response;

import com.ratana.task_management_api.task.enums.AssignmentStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record TaskAssignmentResponse(
        UUID id,
        UUID taskId,
        TaskResponse.UserRef user,
        TaskResponse.UserRef assignedBy,
        AssignmentStatus status,
        Instant assignedAt
) {
}
