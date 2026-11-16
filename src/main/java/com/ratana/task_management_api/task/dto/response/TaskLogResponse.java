package com.ratana.task_management_api.task.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** One entry of a task's audit history (GET /api/v1/tasks/{id}/history). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskLogResponse {
    private UUID id;
    private String action;
    private UUID actorId;
    private String actorName;
    private Map<String, Object> oldValue;
    private Map<String, Object> newValue;
    private String description;
    private Instant createdAt;
}
