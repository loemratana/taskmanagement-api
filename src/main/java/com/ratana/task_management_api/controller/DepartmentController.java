package com.ratana.task_management_api.controller;


import com.ratana.task_management_api.dto.request.AssignManagerRequest;
import com.ratana.task_management_api.dto.request.DepartmentRequest;
import com.ratana.task_management_api.dto.response.ApiResponse;
import com.ratana.task_management_api.dto.response.DepartmentMemberResponse;
import com.ratana.task_management_api.dto.response.DepartmentResponse;
import com.ratana.task_management_api.dto.response.ErrorResponse;
import com.ratana.task_management_api.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Departments", description = "Department management, including hierarchical tree structure")
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DepartmentController {

    private final DepartmentService departmentService;

//    @Operation(summary = "Get department tree", description = "Returns all top-level departments with nested children, forming a full hierarchy tree.")
//    @io.swagger.v3.oas.annotations.responses.ApiResponse(
//            responseCode = "200", description = "Tree retrieved",
//            content = @Content(schema = @Schema(implementation = DepartmentTreeResponse.class)))
//    @GetMapping
//    public ResponseEntity<ApiResponse<List<DepartmentTreeResponse>>> getTree() {
//        return ResponseEntity.ok(ApiResponse.success(departmentService.getTree()));
//    }

    @Operation(summary = "List all departments (flat)", description = "Returns all active departments as a flat list, without hierarchy nesting.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> getAllFlat() {
        return ResponseEntity.ok(ApiResponse.success(departmentService.getAllFlat()));
    }

    @Operation(summary = "Get department details")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Department found",
                    content = @Content(schema = @Schema(implementation = DepartmentResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Department not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentResponse>> getById(
            @Parameter(description = "Department UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(departmentService.getById(id)));
    }

    @Operation(summary = "Create a department", description = "Admin only. Creates a new department, optionally nested under a parent.")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Department created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Department code already exists"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<DepartmentResponse>> create(
            @Valid @RequestBody DepartmentRequest request) {
        DepartmentResponse created = departmentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Department created", created));
    }

    @Operation(summary = "Update a department", description = "Admin only.")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Department updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Department not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Department code already exists")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Department updated", departmentService.update(id, request)));
    }

    @Operation(summary = "Delete a department", description = "Admin only. Soft-deletes the department (deactivates it). Fails if child departments exist.")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Department deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Department has active child departments"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Department not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        departmentService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Department deleted"));
    }

    @Operation(
            summary = "List department members",
            description = "Returns active users belonging to this department, as a minimal picker-friendly DTO. " +
                    "Intended for populating assignee dropdowns (e.g. task creation) without requiring the " +
                    "'users:view' authority."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Members retrieved",
                    content = @Content(schema = @Schema(implementation = DepartmentMemberResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Department not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<List<DepartmentMemberResponse>>> getMembers(
            @Parameter(description = "Department UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(departmentService.getMembers(id)));
    }

    @Operation(
            summary = "Assign department manager",
            description = "Assign a user as department manager"
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{departmentId}/manager")
    public ResponseEntity<ApiResponse<DepartmentResponse>> assignManager(
            @PathVariable UUID departmentId,
            @RequestBody AssignManagerRequest request
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        departmentService.assignManager(
                                departmentId,
                                request.getManagerId()
                        )
                )
        );
    }
}
