package com.ratana.task_management_api.service;

import com.ratana.task_management_api.dto.request.AssignMultipleUsersRequest;
import com.ratana.task_management_api.dto.request.AssignTaskRequest;
import com.ratana.task_management_api.dto.request.CreateTaskRequest;
import com.ratana.task_management_api.dto.request.TaskFilterRequest;
import com.ratana.task_management_api.dto.request.UpdateTaskRequest;
import com.ratana.task_management_api.dto.response.PageResponse;
import com.ratana.task_management_api.dto.response.TaskAssignmentResponse;
import com.ratana.task_management_api.dto.response.TaskResponse;
import com.ratana.task_management_api.model.Task;
import com.ratana.task_management_api.security.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface TaskService {

    TaskResponse createTask(CreateTaskRequest createTaskRequest, UserPrincipal userPrincipal);
    TaskResponse assignTask(UUID taskId, AssignTaskRequest assignTaskRequest);
    TaskResponse updateOwnTask(UUID taskId, UpdateTaskRequest updateTaskRequest);

    TaskResponse getTask(UUID taskId,UserPrincipal userPrincipal);
    TaskResponse changeStatus(UUID id, String status);

    TaskResponse assignTask(UUID taskId, UUID userId);

    /** Adds one or more additional users to a task's multi-assignee list. Already-assigned users are skipped. */
    List<TaskAssignmentResponse> assignUsers(UUID taskId, AssignMultipleUsersRequest request);

    PageResponse<TaskResponse> searchTasks(TaskFilterRequest filter, Pageable pageable, UserPrincipal userPrincipal);

}
