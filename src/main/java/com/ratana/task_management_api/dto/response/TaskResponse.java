package com.ratana.task_management_api.dto.response;

import com.ratana.task_management_api.enums.Priority;
import com.ratana.task_management_api.enums.TaskStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {

    private UUID id;

    private String taskCode;

    private String title;

    private String description;


    // Status
    private TaskStatus status;

    private Priority priority;


    // Delegation chain
    private UserRef creator;

    private UserRef assignedBy;

    private UserRef assignee;


    // Organization
    private DepartmentRef department;


    // Parent task
    private UUID parentTaskId;


    // Timeline
    private LocalDateTime startDate;

    private LocalDate dueDate;

    private LocalDate completedAt;


    // Audit
    private Instant createdAt;

    private Instant updatedAt;


    // Progress
    private Integer progress;


    // Nested User DTO
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserRef {

        private UUID id;

        private String name;

        private String email;

        private String avatarUrl;
    }

    // Nested Department DTO
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepartmentRef {

        private UUID id;

        private String name;
    }

}
