package com.ratana.task_management_api.controller;


import com.ratana.task_management_api.dto.request.DepartmentTelegramGroupRequest;
import com.ratana.task_management_api.dto.response.ApiResponse;
import com.ratana.task_management_api.dto.response.DepartmentTelegramGroupResponse;
import com.ratana.task_management_api.dto.response.ErrorResponse;
import com.ratana.task_management_api.service.DepartmentTelegramGroupService;
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

@Tag(name = "Department Telegram Groups", description = "Admin-only mapping of departments to their Telegram group chat")
@RestController
@RequestMapping("/api/v1/admin/department-telegram-groups")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DepartmentTelegramGroupController {

    private final DepartmentTelegramGroupService groupService;

    @Operation(summary = "List all department Telegram group mappings", description = "Admin only.")
    @PreAuthorize("hasRole(T(com.ratana.task_management_api.security.role.Roles).ADMIN)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentTelegramGroupResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(groupService.getAll()));
    }

    @Operation(summary = "Get the Telegram group mapping for a department", description = "Admin only.")
    @PreAuthorize("hasRole(T(com.ratana.task_management_api.security.role.Roles).ADMIN)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Mapping found",
                    content = @Content(schema = @Schema(implementation = DepartmentTelegramGroupResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "No mapping configured for this department",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{departmentId}")
    public ResponseEntity<ApiResponse<DepartmentTelegramGroupResponse>> getByDepartment(
            @Parameter(description = "Department UUID") @PathVariable UUID departmentId) {
        return ResponseEntity.ok(ApiResponse.success(groupService.getByDepartment(departmentId)));
    }

    @Operation(
            summary = "Configure a department's Telegram group",
            description = "Admin only. Verifies the bot can access the chat before saving."
    )
    @PreAuthorize("hasRole(T(com.ratana.task_management_api.security.role.Roles).ADMIN)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Mapping created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Department not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Department already has a Telegram group configured"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bot cannot access the given chat")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<DepartmentTelegramGroupResponse>> create(
            @Valid @RequestBody DepartmentTelegramGroupRequest request) {
        DepartmentTelegramGroupResponse created = groupService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Telegram group configured", created));
    }

    @Operation(
            summary = "Update a department's Telegram group mapping",
            description = "Admin only. Re-verifies the bot's access if the chat id changes. Department cannot be changed."
    )
    @PreAuthorize("hasRole(T(com.ratana.task_management_api.security.role.Roles).ADMIN)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Mapping updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Mapping not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Department mismatch, or bot cannot access the given chat")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentTelegramGroupResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody DepartmentTelegramGroupRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Telegram group mapping updated", groupService.update(id, request)));
    }

    @Operation(
            summary = "Deactivate a department's Telegram group mapping",
            description = "Admin only. Soft-disables routing without losing audit history."
    )
    @PreAuthorize("hasRole(T(com.ratana.task_management_api.security.role.Roles).ADMIN)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Mapping deactivated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Mapping not found")
    })
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID id) {
        groupService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.success("Telegram group mapping deactivated"));
    }

    @Operation(
            summary = "Send a test message to a department's Telegram group",
            description = "Admin only. Confirms the bot can actually post to the configured chat."
    )
    @PreAuthorize("hasRole(T(com.ratana.task_management_api.security.role.Roles).ADMIN)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Test message sent"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Mapping not found")
    })
    @PostMapping("/{id}/test")
    public ResponseEntity<ApiResponse<Void>> sendTestMessage(@PathVariable UUID id) {
        groupService.sendTestMessage(id);
        return ResponseEntity.ok(ApiResponse.success("Test message sent"));
    }
}
