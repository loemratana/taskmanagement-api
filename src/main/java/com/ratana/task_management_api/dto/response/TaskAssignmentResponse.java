package com.ratana.task_management_api.dto.response;

import com.ratana.task_management_api.enums.AssignmentStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskAssignmentResponse {

    private UUID id;

    private UUID taskId;

    private UserRef user;

    private UserRef assignedBy;

    private AssignmentStatus status;

    private Instant assignedAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserRef {

        private UUID id;

        private String name;

        private String avatarUrl;
    }
}
