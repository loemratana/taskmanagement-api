package com.ratana.task_management_api.task.dto.request;

import com.ratana.task_management_api.task.enums.AssignmentMode;
import com.ratana.task_management_api.task.enums.Priority;
import com.ratana.task_management_api.task.enums.TaskStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskRequest {


    @NotBlank
    @Size(max = 500)
    private String title;
    private String description;
    @NotNull
    private Priority priority;

    /** Ignored on create — new tasks always start as {@link TaskStatus#TODO}. */
    private TaskStatus status;
    @NotNull
    private UUID departmentId;
    /**
     * One person: a normal task. Use at most one of assigneeId, assigneeIds, assignWholeDepartment,
     * assignPositionId.
     */
    private UUID assigneeId;

    /**
     * Several people: creates a group task with one member subtask per person, each with its own
     * submission, review and late tracking. Group progress = average of the members' progress.
     */
    @Size(max = 200, message = "A group task can have at most 200 members.")
    private List<UUID> assigneeIds;

    /** Every active member of departmentId (except the creator) is assigned, per assignmentMode. */
    // Boolean, not boolean: clients that send every field post `null` here, and primitives reject null.
    private Boolean assignWholeDepartment;

    /**
     * Every active holder of this position (except the creator) is assigned, per assignmentMode,
     * whatever their department. The task itself still belongs to departmentId.
     */
    private UUID assignPositionId;

    /**
     * With assigneeIds / assignWholeDepartment: INDIVIDUAL (default) = everyone does their own
     * subtask; SHARED = one task anyone can submit, one approval completes it for all.
     */
    private AssignmentMode assignmentMode;

    /**
     * Creates this task as a subtask. With a group task as parent, assigneeId is required and the
     * new subtask adds that person to the group (e.g. someone who joined the department later).
     */
    private UUID parentTaskId;
    @FutureOrPresent
    private LocalDate startDate;
    @FutureOrPresent
    private LocalDate dueDate;
    private BigDecimal estimatedHours;
    private Set<String> tags;

    /** Null-safe read of assignWholeDepartment (null or false = no). Not a bean getter on purpose. */
    public boolean wholeDepartment() {
        return Boolean.TRUE.equals(assignWholeDepartment);
    }

    /** How the people were chosen, for logs and the audit trail: "department", "position" or "list". */
    public String audienceKind() {
        return wholeDepartment() ? "department" : assignPositionId != null ? "position" : "list";
    }

}
