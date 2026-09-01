package com.ratana.task_management_api.dto.request;

import com.ratana.task_management_api.enums.TaskStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TaskStatusUpdateRequest
        (
                @NotNull UUID taskId,
                @NotNull TaskStatus newStatus
        ) {
}
