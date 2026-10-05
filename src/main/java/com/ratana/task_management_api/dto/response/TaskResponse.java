package com.ratana.task_management_api.dto.response;

import com.ratana.task_management_api.enums.Priority;
import com.ratana.task_management_api.enums.TaskStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

    private TaskStatus status;

    private Priority priority;


    private UserRef creator;

    private UserRef assignedBy;

    private UserRef assignee;

    private List<CommentResponse> comments;
    private List<AttachmentResponse> attachments;

    /** Cheap counts for list/card views. Populated on every response;
     *  {@code comments}/{@code attachments} above are only populated on the single-task detail view. */
    private Integer commentCount;
    private Integer attachmentCount;


    private List<UserRef> assignees;

    private DepartmentRef department;

    private UUID parentTaskId;

    private LocalDateTime startDate;

    private LocalDate dueDate;

    private LocalDate completedAt;

    private Instant createdAt;

    private Instant updatedAt;

    private Integer progress;

    private Integer rating;

    private TaskStatus reviewDecision;

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
