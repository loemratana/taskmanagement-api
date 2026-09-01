package com.ratana.task_management_api.dto.response;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AttachmentResponse(
        UUID id,
        String originalName,
        String fileType,
        Long fileSize,
        String downloadUrl,
        UUID uploadedByUserId,
        String uploadedByName,
        UUID taskId,
        Instant createdAt
) {
}
