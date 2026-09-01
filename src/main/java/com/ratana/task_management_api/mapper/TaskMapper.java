package com.ratana.task_management_api.mapper;


import com.ratana.task_management_api.dto.request.CreateTaskRequest;
import com.ratana.task_management_api.dto.response.TaskResponse;
import com.ratana.task_management_api.enums.Priority;
import com.ratana.task_management_api.enums.TaskStatus;
import com.ratana.task_management_api.model.Department;
import com.ratana.task_management_api.model.Task;
import com.ratana.task_management_api.model.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TaskMapper {

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
                .department(
                        TaskResponse.DepartmentRef.builder()
                                .id(task.getDepartment().getId())
                                .name(task.getDepartment().getName())
                                .build()
                )
                .dueDate(task.getDueDate())
                .createdAt(task.getCreatedAt())

                .updatedAt(task.getUpdatedAt())

                .build();
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
