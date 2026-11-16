package com.ratana.task_management_api.task.controller;

import com.ratana.task_management_api.task.dto.request.ReviewSubmissionRequest;
import com.ratana.task_management_api.task.dto.request.SubmitWorkRequest;
import com.ratana.task_management_api.common.response.ApiResponse;
import com.ratana.task_management_api.task.dto.response.TaskSubmissionResponse;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.task.service.TaskSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks/{taskId}/submissions")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class TaskSubmissionController {

    private final TaskSubmissionService submissionService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Submit work",
            description = "Assignee submits work with an optional note, images and files. Moves the task to IN_REVIEW. "
                    + "When today (business timezone) is after the task's dueDate, lateReason is required "
                    + "(400 otherwise); the submission is then stored with late=true.")
    public ResponseEntity<ApiResponse<TaskSubmissionResponse>> submit(
            @PathVariable UUID taskId,
            @Valid @ModelAttribute SubmitWorkRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TaskSubmissionResponse body = submissionService.submit(taskId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Work submitted", body));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List submissions of a task (newest first)")
    public ResponseEntity<ApiResponse<List<TaskSubmissionResponse>>> list(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(submissionService.list(taskId, principal)));
    }

    @GetMapping(value = "/{submissionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get one submission")
    public ResponseEntity<ApiResponse<TaskSubmissionResponse>> get(
            @PathVariable UUID taskId,
            @PathVariable UUID submissionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(submissionService.get(taskId, submissionId, principal)));
    }

    @PostMapping(value = "/{submissionId}/review",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Approve or reject a submission")
    public ResponseEntity<ApiResponse<TaskSubmissionResponse>> review(
            @PathVariable UUID taskId,
            @PathVariable UUID submissionId,
            @Valid @RequestBody ReviewSubmissionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Submission reviewed",
                submissionService.review(taskId, submissionId, request, principal)));
    }
}
