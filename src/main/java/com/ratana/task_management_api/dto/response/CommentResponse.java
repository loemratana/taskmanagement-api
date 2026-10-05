package com.ratana.task_management_api.dto.response;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Comment on a task")
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentResponse {

    private UUID id;

    private UUID taskId;

    private String content;

    private TaskResponse.UserRef author;

    private Instant createdAt;

    private Instant updatedAt;
}
