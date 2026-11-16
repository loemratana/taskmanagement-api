package com.ratana.task_management_api.task.controller;

import com.ratana.task_management_api.task.dto.response.TaskCountsResponse;
import com.ratana.task_management_api.task.dto.request.AssignMultipleUsersRequest;
import com.ratana.task_management_api.task.dto.request.AssignTaskRequest;
import com.ratana.task_management_api.task.dto.request.CreateTaskRequest;
import com.ratana.task_management_api.task.reminder.ReminderSettingsUpdateRequest;
import com.ratana.task_management_api.task.dto.request.TaskCommentRequest;
import com.ratana.task_management_api.task.dto.request.TaskFilterRequest;
import com.ratana.task_management_api.task.dto.request.UpdateTaskRequest;
import com.ratana.task_management_api.common.response.ApiResponse;
import com.ratana.task_management_api.common.response.ErrorResponse;
import com.ratana.task_management_api.task.dto.response.ManagerAssignedKpiResponse;
import com.ratana.task_management_api.common.pagination.PageResponse;
import com.ratana.task_management_api.task.dto.response.TaskAssignmentResponse;
import com.ratana.task_management_api.task.reminder.ReminderSettingsResponse;
import com.ratana.task_management_api.task.dto.response.TaskCommentResponse;
import com.ratana.task_management_api.task.dto.response.TaskLogResponse;
import com.ratana.task_management_api.task.dto.response.TaskResponse;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.task.service.TaskAuditService;
import com.ratana.task_management_api.task.service.TaskCommentService;
import com.ratana.task_management_api.task.reminder.TaskReminderService;
import com.ratana.task_management_api.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final TaskCommentService taskCommentService;
    private final TaskReminderService taskReminderService;
    private final TaskAuditService taskAuditService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create task", description = "Create a new task and optionally assign a user.")
    @PreAuthorize("hasAuthority('tasks:create')")
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            @Valid @RequestBody CreateTaskRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        TaskResponse response = taskService.createTask(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search tasks",
            description = "Lists tasks visible to the caller, scoped by their permissions (own/team/department/all). " +
                    "All filter parameters are optional and combine with AND. Supports standard Spring pagination/sort " +
                    "params, e.g. ?page=0&size=20&sort=createdAt,desc")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of matching tasks")
    public ResponseEntity<ApiResponse<PageResponse<TaskResponse>>> searchTasks(
            @ParameterObject @ModelAttribute TaskFilterRequest filter,
            @ParameterObject Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(taskService.searchTasks(filter, pageable, principal)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a task")
    public ResponseEntity<ApiResponse<TaskResponse>> getTask(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(taskService.getTask(id, principal)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete (archive) a task",
            description = "Soft delete: the task and its subtasks are hidden from lists, dashboards and reminders. "
                    + "Requires tasks:delete and visibility of the task.")
    public ResponseEntity<ApiResponse<Void>> archiveTask(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        taskService.archiveTask(id, principal);
        return ResponseEntity.ok(ApiResponse.success("Task deleted"));
    }

    @GetMapping(value = "/counts", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Count tasks by status",
            description = "Exact counts for the tasks GET /tasks would return with the same filters (taskStatus and "
                    + "overdue are ignored, since they are the breakdown). Use for dashboards instead of fetching pages.")
    public ResponseEntity<ApiResponse<TaskCountsResponse>> countTasks(
            @ParameterObject @ModelAttribute TaskFilterRequest filter,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(taskService.countTasks(filter, principal)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a task",
            description = "Updates task fields. Does not change status or assignee.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Task updated",
                    content = @Content(schema = @Schema(implementation = TaskResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not permitted",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<TaskResponse>> update(
            @Parameter(description = "Task id") @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskRequest request) {
        return ResponseEntity.ok(ApiResponse.success(taskService.updateOwnTask(id, request)));
    }

    @PatchMapping("/{id}/assignee")
    @Operation(summary = "Assign a task")
    @PreAuthorize("hasAnyAuthority('tasks:assign:all','tasks:assign:department','tasks:assign:team')")
    public ResponseEntity<ApiResponse<TaskResponse>> assignTask(
            @PathVariable UUID id,
            @Valid @RequestBody AssignTaskRequest request) {
        return ResponseEntity.ok(ApiResponse.success(taskService.assignTask(id, request)));
    }

    @PostMapping(value = "/{id}/assignees", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Add collaborators to a task",
            description = "Adds users to the task beyond its primary assignee. Users already on the task are skipped; "
                    + "the response lists only the newly added assignments.")
    @PreAuthorize("hasAnyAuthority('tasks:assign:all','tasks:assign:department','tasks:assign:team')")
    public ResponseEntity<ApiResponse<List<TaskAssignmentResponse>>> assignAdditionalUsers(
            @PathVariable UUID id,
            @Valid @RequestBody AssignMultipleUsersRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(taskService.assignAdditionalUsers(id, request)));
    }

    @GetMapping(value = "/me/assigned-kpi", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "KPIs for tasks I assigned",
            description = "Status counts, overdue count, per-assignee workload and performance, and completions over "
                    + "the last 7 days, for active tasks the caller assigned.")
    public ResponseEntity<ApiResponse<ManagerAssignedKpiResponse>> getAssignedKpi(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(taskService.getAssignedKpi(principal)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change task status",
            description = "Body: {\"status\": \"...\", \"reason\": \"...\"}. IN_REVIEW, APPROVED and REJECTED are set "
                    + "only by the submission and review endpoints (409). Reopening a DONE/CANCELLED task to TODO "
                    + "needs tasks:transition:reopen:all and a reason. The assignee may move IN_PROGRESS -> BLOCKED "
                    + "(reason required) and BLOCKED -> IN_PROGRESS.")
    public ResponseEntity<ApiResponse<TaskResponse>> changeStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(
                taskService.changeStatus(id, body.get("status"), body.get("reason"))));
    }

    @PostMapping(value = "/{id}/status-override", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Override task status (admin)",
            description = "Moves a task to any status outside the normal workflow. Body: {\"status\", \"reason\"}; "
                    + "reason is required and the change is recorded in the task history.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TaskResponse>> overrideStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(
                taskService.overrideStatus(id, body.get("status"), body.get("reason"))));
    }

    @GetMapping(value = "/{id}/history", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Task history", description = "Audit trail of the task, oldest first. Anyone who can view the task may read it.")
    public ResponseEntity<ApiResponse<List<TaskLogResponse>>> history(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(taskAuditService.history(id, principal)));
    }

    @PostMapping(value = "/{id}/comments", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Add a comment to a task",
            description = "Anyone who can view the task (assignee, creator, or a broader view permission) can comment.")
    public ResponseEntity<ApiResponse<TaskCommentResponse>> addComment(
            @PathVariable UUID id,
            @Valid @RequestBody TaskCommentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TaskCommentResponse response = taskCommentService.addComment(id, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping(value = "/{id}/comments", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List a task's comments", description = "Oldest first.")
    public ResponseEntity<ApiResponse<List<TaskCommentResponse>>> listComments(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(taskCommentService.listComments(id, principal)));
    }

    @GetMapping(value = "/{id}/reminder-settings", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a task's reminder settings",
            description = "Returns the persisted configuration if one exists, otherwise the system defaults.")
    public ResponseEntity<ApiResponse<ReminderSettingsResponse>> getReminderSettings(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(taskReminderService.getSettings(id, principal)));
    }

    @PatchMapping(value = "/{id}/reminder-settings", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a task's reminder settings",
            description = "Partial update — enable/disable reminders, change the timezone, or replace the reminder times. "
                    + "Fields left null are unchanged.")
    public ResponseEntity<ApiResponse<ReminderSettingsResponse>> updateReminderSettings(
            @PathVariable UUID id,
            @Valid @RequestBody ReminderSettingsUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(taskReminderService.updateSettings(id, request, principal)));
    }
}
