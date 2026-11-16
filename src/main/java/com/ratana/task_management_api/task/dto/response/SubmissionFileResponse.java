package com.ratana.task_management_api.task.dto.response;

import com.ratana.task_management_api.task.enums.SubmissionFileKind;
import lombok.Builder;

import java.util.UUID;

@Builder
public record SubmissionFileResponse(
        UUID id,
        SubmissionFileKind kind,
        String originalName,
        String contentType,
        long size,
        String url          // signed, short-lived
) {
}
