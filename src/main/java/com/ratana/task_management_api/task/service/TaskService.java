package com.ratana.task_management_api.task.service;

import com.ratana.task_management_api.task.dto.response.TaskCountsResponse;
import com.ratana.task_management_api.task.dto.request.AssignMultipleUsersRequest;
import com.ratana.task_management_api.task.dto.request.AssignTaskRequest;
import com.ratana.task_management_api.task.dto.request.CreateTaskRequest;
import com.ratana.task_management_api.task.dto.request.TaskFilterRequest;
import com.ratana.task_management_api.task.dto.request.UpdateTaskRequest;
import com.ratana.task_management_api.task.dto.response.ManagerAssignedKpiResponse;
import com.ratana.task_management_api.common.pagination.PageResponse;
import com.ratana.task_management_api.task.dto.response.TaskAssignmentResponse;
import com.ratana.task_management_api.task.dto.response.TaskResponse;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface TaskService {

    TaskResponse createTask(CreateTaskRequest createTaskRequest, UserPrincipal userPrincipal);
    TaskResponse assignTask(UUID taskId, AssignTaskRequest assignTaskRequest);
    TaskResponse updateOwnTask(UUID taskId, UpdateTaskRequest updateTaskRequest);

    TaskResponse getTask(UUID taskId,UserPrincipal userPrincipal);

    /** Soft-deletes the task and its subtasks (is_active = false). Archived tasks behave as not found. */
    void archiveTask(UUID taskId, UserPrincipal userPrincipal);
    TaskResponse changeStatus(UUID id, String status);

    /** {@code reason} is required when reopening a DONE/CANCELLED task, optional otherwise. */
    TaskResponse changeStatus(UUID id, String status, String reason);

    /** Admin-only move outside the status graph; reason required, always audited. */
    TaskResponse overrideStatus(UUID id, String status, String reason);

    TaskResponse assignTask(UUID taskId, UUID userId);

    PageResponse<TaskResponse> searchTasks(TaskFilterRequest filter, Pageable pageable, UserPrincipal userPrincipal);

    /** Exact counts per status (plus total and overdue) for the same tasks searchTasks would return. */
    TaskCountsResponse countTasks(TaskFilterRequest filter, UserPrincipal userPrincipal);

    List<TaskAssignmentResponse> assignAdditionalUsers(UUID taskId, AssignMultipleUsersRequest request);

    ManagerAssignedKpiResponse getAssignedKpi(UserPrincipal userPrincipal);

}
