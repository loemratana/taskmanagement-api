package com.ratana.task_management_api.task.mapper;


import com.ratana.task_management_api.task.dto.request.CreateTaskRequest;
import com.ratana.task_management_api.task.dto.response.TaskResponse;
import com.ratana.task_management_api.task.enums.Priority;
import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TaskMapper {

    /** completedAt is sent as a business-timezone date, matching dueDate. */
    private java.time.ZoneId reportZone = java.time.ZoneId.of("Asia/Phnom_Penh");

    @org.springframework.beans.factory.annotation.Value("${app.reminder.default-timezone:Asia/Phnom_Penh}")
    void setReportZone(String zone) {
        this.reportZone = java.time.ZoneId.of(zone);
    }

    private java.time.Clock clock = java.time.Clock.systemUTC();

    @org.springframework.beans.factory.annotation.Autowired
    void setClock(java.time.Clock clock) {
        this.clock = clock;
    }

    /** Same statuses as TaskSpecifications#overdue: a finished task is never overdue. */
    private static final java.util.Set<TaskStatus> FINISHED =
            java.util.EnumSet.of(TaskStatus.DONE, TaskStatus.APPROVED, TaskStatus.CANCELLED);

    /**
     * Past its due date (business timezone) and not finished. For a submittable task this is exactly
     * when submitting requires a late reason.
     */
    private boolean isOverdue(Task task) {
        if (task.getDueDate() == null || FINISHED.contains(task.getStatus())) {
            return false;
        }
        java.time.LocalDate today = java.time.LocalDate.ofInstant(clock.instant(), reportZone);
        return today.isAfter(task.getDueDate());
    }

    private static boolean isCompleted(TaskStatus status) {
        return status == TaskStatus.APPROVED || status == TaskStatus.DONE;
    }

    public Task toEntity(CreateTaskRequest createTaskRequest) {
        return Task.builder()
                .title(createTaskRequest.getTitle())

                .description(createTaskRequest.getDescription())

                .status(
                        createTaskRequest.getStatus() != null
                                ? createTaskRequest.getStatus()
                                : TaskStatus.TODO
                )

                .priority(
                        createTaskRequest.getPriority() != null
                                ? createTaskRequest.getPriority()
                                : Priority.MEDIUM
                )

                .startDate(createTaskRequest.getStartDate())

                .dueDate(createTaskRequest.getDueDate())

                .build();
    }

    public TaskResponse toResponse(Task task) {
        if (task == null) {
            return null;
        }
        return TaskResponse.builder()

                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(task.getPriority())
                .creator(
                        mapUser(task.getCreator())
                )
                .assignedBy(
                        mapUser(task.getAssignedBy())
                )
                .assignee(
                        mapUser(task.getAssignee())
                )
                .assignees(mapAssignees(task))
                .department(
                        TaskResponse.DepartmentRef.builder()
                                .id(task.getDepartment().getId())
                                .name(task.getDepartment().getName())
                                .build()
                )
                // getParentTask().getId() reads the id off the lazy proxy without loading the parent.
                .parentTaskId(task.getParentTask() != null ? task.getParentTask().getId() : null)
                .groupTask(task.isGroupTask())
                .memberCount(task.isGroupTask() ? task.countableSubtasks().size() : null)
                .completedMemberCount(task.isGroupTask()
                        ? (int) task.countableSubtasks().stream()
                                .filter(subtask -> isCompleted(subtask.getStatus())).count()
                        : null)
                .startDate(task.getStartDate() != null ? task.getStartDate().atStartOfDay() : null)
                .dueDate(task.getDueDate())
                .overdue(isOverdue(task))
                .completedAt(task.getCompletedAt() != null
                        ? task.getCompletedAt().atZone(reportZone).toLocalDate()
                        : null)
                .progress(task.computeProgress())
                .createdAt(task.getCreatedAt())

                .updatedAt(task.getUpdatedAt())

                .build();
    }

    /**
     * Primary assignee first, then additional collaborators, deduplicated by user id.
     * Must run inside a transaction — it reads the lazy {@code assignments} collection.
     */
    private List<TaskResponse.UserRef> mapAssignees(Task task) {
        java.util.Map<java.util.UUID, TaskResponse.UserRef> byId = new java.util.LinkedHashMap<>();
        if (task.getAssignee() != null) {
            byId.put(task.getAssignee().getId(), mapUser(task.getAssignee()));
        }
        if (task.getAssignments() != null) {
            for (var assignment : task.getAssignments()) {
                User user = assignment.getUser();
                if (user != null) {
                    byId.putIfAbsent(user.getId(), mapUser(user));
                }
            }
        }
        return new java.util.ArrayList<>(byId.values());
    }

    public TaskResponse.UserRef mapUser(User user) {
        if (user == null) {
            return null;
        }
        return TaskResponse.UserRef.builder()
                .id(user.getId())

                .name(user.getFullName())

                .email(user.getEmail())

                .avatarUrl(user.getAvatarUrl())

                .build();


    }

    private TaskResponse.DepartmentRef mapDepartment(Department department
    ) {
        if (department == null) {
            return null;
        }
        return TaskResponse.DepartmentRef.builder()

                .id(department.getId())

                .name(department.getName())

                .build();
    }

    public List<TaskResponse> toListResponse(List<Task> taskList) {
        if (taskList == null) {
            return null;
        }

        return taskList.stream().map(this::toResponse).toList();
    }
}
