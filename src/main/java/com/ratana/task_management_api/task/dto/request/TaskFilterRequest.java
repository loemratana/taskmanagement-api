package com.ratana.task_management_api.task.dto.request;

import com.ratana.task_management_api.task.enums.Priority;
import com.ratana.task_management_api.task.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskFilterRequest {
    private TaskStatus taskStatus;
    private Priority priority;

    private UUID  departmentId;
    private UUID assigneeId;
    private UUID  createdById;
    /** Tasks this user assigned (Task.assignedBy). */
    private UUID assignedById;
    private String titleContains;
    /** true = only top-level tasks (no subtasks), matching the task tabs. */
    private Boolean topLevelOnly;
    /**
     * With assigneeId: true = leave out subtasks whose parent is also assigned to that person (the app
     * nests them in the parent's card). Unlike topLevelOnly it keeps a person's own group-task subtask,
     * so the employee dashboard's numbers match the rows of their "My tasks" list.
     */
    private Boolean excludeNestedSubtasks;
    /** true = only overdue tasks: due before today and not DONE/APPROVED/CANCELLED. */
    private Boolean overdue;
}
