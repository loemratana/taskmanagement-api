package com.ratana.task_management_api.task.dto.request;

import com.ratana.task_management_api.task.enums.TaskStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TaskStatusUpdateRequest
        (
                @NotNull UUID taskId,
                @NotNull TaskStatus newStatus
        ) {
}
