package com.ratana.task_management_api.task.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewSubmissionRequest(
        @NotNull(message = "Decision is required (APPROVED or REJECTED).") Decision decision,
        @Size(max = 2000, message = "Comment must be at most 2000 characters.") String comment
) {
    public enum Decision { APPROVED, REJECTED }
}
