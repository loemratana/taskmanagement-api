package com.ratana.task_management_api.task.controller;

import com.ratana.task_management_api.common.response.ApiResponse;
import com.ratana.task_management_api.common.pagination.PageResponse;
import com.ratana.task_management_api.task.dto.response.TaskSubmissionResponse;
import com.ratana.task_management_api.task.enums.SubmissionStatus;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.task.service.TaskSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/submissions")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class MySubmissionController {

    private final TaskSubmissionService submissionService;

    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List my submissions",
            description = "Submissions made by the current user across all tasks, newest first. "
                    + "Optional status filter: PENDING, APPROVED, REJECTED.")
    public ResponseEntity<ApiResponse<PageResponse<TaskSubmissionResponse>>> listMine(
            @RequestParam(required = false) SubmissionStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(submissionService.listMine(status, pageable, principal)));
    }

    @GetMapping(value = "/pending-review", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List submissions waiting for my review",
            description = "PENDING submissions the current user can review: every department with "
                    + "tasks:transition:review:all, own department with tasks:transition:review:department. "
                    + "Oldest first.")
    public ResponseEntity<ApiResponse<PageResponse<TaskSubmissionResponse>>> listPendingReview(
            @ParameterObject @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.ASC)
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(submissionService.listPendingReview(pageable, principal)));
    }
}
