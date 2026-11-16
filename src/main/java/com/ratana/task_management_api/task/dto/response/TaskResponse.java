package com.ratana.task_management_api.task.dto.response;

import com.ratana.task_management_api.task.enums.Priority;
import com.ratana.task_management_api.task.enums.TaskStatus;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /** Everyone on the task: the primary assignee plus collaborators from task_assignments, deduplicated. */
    private java.util.List<UserRef> assignees;


    // Organization
    private DepartmentRef department;


    // Parent task
    private UUID parentTaskId;

    /**
     * Given to several people: no assignee, one member subtask per person, status follows the
     * members and the task closes itself once every member is approved. progress is the average.
     */
    private boolean groupTask;

    /** Group tasks only: members counted (active, not cancelled), and how many are approved/done. */
    private Integer memberCount;
    private Integer completedMemberCount;


    // Timeline
    private LocalDateTime startDate;

    private LocalDate dueDate;

    /**
     * Past dueDate (business timezone) and not DONE/APPROVED/CANCELLED. When true on a TODO, IN_PROGRESS
     * or REJECTED task, submitting work requires a lateReason.
     */
    private boolean overdue;

    private LocalDate completedAt;

    private Integer commentCount;
    private Integer attachmentCount;



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
