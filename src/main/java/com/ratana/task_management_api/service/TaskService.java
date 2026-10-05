package com.ratana.task_management_api.service;

import com.ratana.task_management_api.dto.request.*;
import com.ratana.task_management_api.dto.response.KpiSummaryResponse;
import com.ratana.task_management_api.dto.response.ManagerAssignedKpiResponse;
import com.ratana.task_management_api.dto.response.PageResponse;
import com.ratana.task_management_api.dto.response.TaskAssignmentResponse;
import com.ratana.task_management_api.dto.response.TaskResponse;
import com.ratana.task_management_api.dto.response.TaskReviewResponse;
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

    List<TaskResponse> createTasksForGroup(BulkCreateTaskRequest request, UserPrincipal userPrincipal);


    /** Approves or rejects a task's submitted work with a rating + comment. One review per task — a re-review overwrites it. */
    TaskReviewResponse reviewTask(UUID id, ReviewTaskRequest request);

    /** The rating left on this task, if it's been reviewed yet. */
    TaskReviewResponse getReview(UUID taskId, UserPrincipal userPrincipal);

    TaskResponse assignTask(UUID taskId, UUID userId);

    /** Adds one or more additional users to a task's multi-assignee list. Already-assigned users are skipped. */
    List<TaskAssignmentResponse> assignUsers(UUID taskId, AssignMultipleUsersRequest request);

    PageResponse<TaskResponse> searchTasks(TaskFilterRequest filter, Pageable pageable, UserPrincipal userPrincipal);

    /** Completion-rate KPI computed from the caller's own assigned tasks. */
    KpiSummaryResponse getMyKpiSummary(UserPrincipal userPrincipal);

    /** Dashboard KPI counts for tasks the caller (a manager) assigned. */
    ManagerAssignedKpiResponse getManagerAssignedKpiSummary(UserPrincipal userPrincipal);

}
