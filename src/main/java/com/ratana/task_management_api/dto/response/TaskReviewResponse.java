package com.ratana.task_management_api.dto.response;

import com.ratana.task_management_api.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskReviewResponse {

    private UUID id;

    private UUID taskId;

    private TaskResponse.UserRef reviewer;

    private Integer rating;

    private String comment;

    private TaskStatus decision;

    private Instant createdAt;

    private Instant updatedAt;
}
