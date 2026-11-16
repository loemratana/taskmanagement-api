package com.ratana.task_management_api.task.dto.response;

import com.ratana.task_management_api.task.enums.SubmissionStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record TaskSubmissionResponse(
        UUID id,
        UUID taskId,
        String taskTitle,
        String taskStatus,
        int round,
        String note,
        boolean late,
        String lateReason,
        SubmissionStatus status,
        UUID submittedById,
        String submittedByName,
        Instant submittedAt,
        UUID reviewedById,
        String reviewedByName,
        String reviewComment,
        Instant reviewedAt,
        List<SubmissionFileResponse> images,
        List<SubmissionFileResponse> files
) {
}
