package com.ratana.task_management_api.controller;

import com.ratana.task_management_api.dto.request.AssignMultipleUsersRequest;
import com.ratana.task_management_api.dto.request.AssignTaskRequest;
import com.ratana.task_management_api.dto.request.CreateTaskRequest;
import com.ratana.task_management_api.dto.request.ReminderSettingsUpdateRequest;
import com.ratana.task_management_api.dto.request.TaskCommentRequest;
import com.ratana.task_management_api.dto.request.TaskFilterRequest;
import com.ratana.task_management_api.dto.request.UpdateTaskRequest;
import com.ratana.task_management_api.dto.response.ApiResponse;
import com.ratana.task_management_api.dto.response.ErrorResponse;
import com.ratana.task_management_api.dto.response.PageResponse;
import com.ratana.task_management_api.dto.response.ReminderSettingsResponse;
import com.ratana.task_management_api.dto.response.TaskAssignmentResponse;
import com.ratana.task_management_api.dto.response.TaskCommentResponse;
import com.ratana.task_management_api.dto.response.TaskResponse;
import com.ratana.task_management_api.security.UserPrincipal;
import com.ratana.task_management_api.service.TaskCommentService;
import com.ratana.task_management_api.service.TaskReminderService;
import com.ratana.task_management_api.service.TaskService;
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
    @PreAuthorize("hasAnyAuthority('tasks:assign:all','tasks:assign:department')")
    public ResponseEntity<ApiResponse<TaskResponse>> assignTask(
            @PathVariable UUID id,
            @Valid @RequestBody AssignTaskRequest request) {
        return ResponseEntity.ok(ApiResponse.success(taskService.assignTask(id, request)));
    }

    @PostMapping(value = "/{id}/assignees", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Add assignees to a task",
            description = "Adds one or more additional users to a task's multi-assignee list. Users already assigned are skipped.")
    @PreAuthorize("hasAnyAuthority('tasks:assign:all','tasks:assign:department')")
    public ResponseEntity<ApiResponse<List<TaskAssignmentResponse>>> assignUsers(
            @PathVariable UUID id,
            @Valid @RequestBody AssignMultipleUsersRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(taskService.assignUsers(id, request)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change task status")
    public ResponseEntity<ApiResponse<TaskResponse>> changeStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return ResponseEntity.ok(ApiResponse.success(taskService.changeStatus(id, status)));
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
