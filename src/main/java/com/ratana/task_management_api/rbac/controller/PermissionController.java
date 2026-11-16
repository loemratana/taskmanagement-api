package com.ratana.task_management_api.rbac.controller;


import com.ratana.task_management_api.rbac.dto.request.PermissionRequest;
import com.ratana.task_management_api.common.response.ApiResponse;
import com.ratana.task_management_api.rbac.dto.response.PermissionResponse;
import com.ratana.task_management_api.rbac.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
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

@Tag(name = "Permissions", description = "Manage granular permissions (module + action pairs)")
@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PermissionController {
    private final PermissionService permissionService;

    @Operation(summary = "List all permissions")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('permission:read')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(permissionService.getAll()));
    }

    @Operation(summary = "Get permission details")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('permission:read')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(permissionService.getById(id)));
    }

    @Operation(summary = "Create a permission")
    @PreAuthorize("hasRole('ADMIN') OR hasAuthority('permission:create')")
    @PostMapping
    public ResponseEntity<ApiResponse<PermissionResponse>> create(@Valid @RequestBody PermissionRequest request) {
        PermissionResponse created = permissionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Permission created", created));
    }

    @Operation(summary = "Update a permission")
    @PreAuthorize("hasAuthority('permission:update')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody PermissionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Permission updated", permissionService.update(id, request)));
    }

    @Operation(summary = "Delete a permission", description = "Soft-deletes the permission (deactivates it).")
    @PreAuthorize("hasAuthority('permission:delete')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        permissionService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Permission deleted"));
    }

}
