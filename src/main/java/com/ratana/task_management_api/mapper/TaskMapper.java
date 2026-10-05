package com.ratana.task_management_api.mapper;


import com.ratana.task_management_api.dto.request.CreateTaskRequest;
import com.ratana.task_management_api.dto.response.AttachmentResponse;
import com.ratana.task_management_api.dto.response.CommentResponse;
import com.ratana.task_management_api.dto.response.TaskResponse;
import com.ratana.task_management_api.enums.Priority;
import com.ratana.task_management_api.enums.TaskStatus;
import com.ratana.task_management_api.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TaskMapper {

    private final AttachmentMapper attachmentMapper;

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

                .estimatedHours(createTaskRequest.getEstimatedHours())

                .tags(createTaskRequest.getTags())

                .build();
    }

    public TaskResponse toResponse(Task task) {
        return toResponse(task, null);
    }

    /**
     * Single-task detail view: includes comments and attachments.
     * Only use where the task was loaded with those fetched (e.g. TaskRepository#findById) —
     * never on list/paged results, or this triggers a lazy-load N+1 per row.
     */
    public TaskResponse toDetailResponse(Task task, TaskReview review) {
        TaskResponse response = toResponse(task, review);
        if (response == null) {
            return null;
        }
        response.setComments(mapComments(task.getComments()));
        response.setAttachments(mapAttachments(task.getAttachments()));
        response.setCommentCount(response.getComments().size());
        response.setAttachmentCount(response.getAttachments().size());
        return response;
    }

    /**
     * List/page view: same as {@link #toResponse(Task, TaskReview)} plus cheap counts
     * (batch-loaded by the caller, e.g. via TaskCommentRepository#countGroupedByTaskIdIn) —
     * never load the full comments/attachments collections for a page of tasks.
     */
    public TaskResponse toResponse(Task task, TaskReview review, Integer commentCount, Integer attachmentCount) {
        TaskResponse response = toResponse(task, review);
        if (response == null) {
            return null;
        }
        response.setCommentCount(commentCount != null ? commentCount : 0);
        response.setAttachmentCount(attachmentCount != null ? attachmentCount : 0);
        return response;
    }

    /** @param review the task's review row if one exists yet (batch-loaded by the caller), else null. */
    public TaskResponse toResponse(Task task, TaskReview review) {
        if (task == null) {
            return null;
        }
        return TaskResponse.builder()

                .id(task.getId())
                .taskCode(task.getTaskCode())
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
                .assignees(buildAssignees(task))
                .department(
                        TaskResponse.DepartmentRef.builder()
                                .id(task.getDepartment().getId())
                                .name(task.getDepartment().getName())
                                .build()
                )
                .dueDate(task.getDueDate())
                .startDate(
                        task.getStartDate() != null ? task.getStartDate().atStartOfDay() : null
                )
                .parentTaskId(
                        task.getParentTask() != null ? task.getParentTask().getId() : null
                )
                .progress(task.getProgress())
                .rating(review != null ? review.getRating() : null)
                .reviewDecision(review != null ? review.getDecision() : null)
                .completedAt(
                        task.getCompletedAt() != null
                                ? task.getCompletedAt().atZone(ZoneId.systemDefault()).toLocalDate()
                                : null
                )
                .createdAt(task.getCreatedAt())

                .updatedAt(task.getUpdatedAt())

                .build();
    }
    /** Primary assignee plus additional collaborators (task_assignments), deduplicated by user id. */
    private List<TaskResponse.UserRef> buildAssignees(Task task) {
        Map<UUID, TaskResponse.UserRef> byId = new LinkedHashMap<>();

        if (task.getAssignee() != null) {
            byId.put(task.getAssignee().getId(), mapUser(task.getAssignee()));
        }

        for (TaskAssignment assignment : task.getAssignments()) {
            User user = assignment.getUser();
            if (user != null) {
                byId.putIfAbsent(user.getId(), mapUser(user));
            }
        }

        return new ArrayList<>(byId.values());
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
    private List<AttachmentResponse> mapAttachments(List<Attachment> attachments) {
        if (attachments == null) {
            return List.of();
        }
        return attachments.stream()
                .map(attachmentMapper::toResponse)
                .toList();
    }

    private List<CommentResponse>  mapComments(List<TaskComment> comments) {
        if (comments == null) {
            return null;
        }

        return comments
                .stream()
                .map(comment ->

                    CommentResponse.builder()
                            .id(comment.getId())
                            .taskId(comment.getTask() !=null ?comment.getTask().getId() : null)
                            .content(comment.getContent())
                            .author(mapUser(comment.getUser()))
                            .createdAt(comment.getCreatedAt())
                            .updatedAt(comment.getUpdatedAt())
                            .build()).toList();
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
