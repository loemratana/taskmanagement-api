package com.ratana.task_management_api.controller;


import com.ratana.task_management_api.dto.request.PermissionRequest;
import com.ratana.task_management_api.dto.response.ApiResponse;
import com.ratana.task_management_api.dto.response.PermissionResponse;
import com.ratana.task_management_api.service.PermissionService;
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
    @PreAuthorize("hasRole(T(com.ratana.task_management_api.security.role.Roles).ADMIN) "
            + "or hasAuthority(T(com.ratana.task_management_api.security.permission.Permissions).PERMISSIONS_READ)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(permissionService.getAll()));
    }

    @Operation(summary = "Get permission details")
    @PreAuthorize("hasRole(T(com.ratana.task_management_api.security.role.Roles).ADMIN) "
            + "or hasAuthority(T(com.ratana.task_management_api.security.permission.Permissions).PERMISSIONS_READ)")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(permissionService.getById(id)));
    }

    @Operation(summary = "Create a permission")
    @PreAuthorize("hasRole(T(com.ratana.task_management_api.security.role.Roles).ADMIN) "
            + "or hasAuthority(T(com.ratana.task_management_api.security.permission.Permissions).PERMISSIONS_CREATE)")
    @PostMapping
    public ResponseEntity<ApiResponse<PermissionResponse>> create(@Valid @RequestBody PermissionRequest request) {
        PermissionResponse created = permissionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Permission created", created));
    }

    @Operation(summary = "Update a permission")
    @PreAuthorize("hasAuthority(T(com.ratana.task_management_api.security.permission.Permissions).PERMISSIONS_UPDATE)")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody PermissionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Permission updated", permissionService.update(id, request)));
    }

    @Operation(summary = "Delete a permission", description = "Soft-deletes the permission (deactivates it).")
    @PreAuthorize("hasAuthority(T(com.ratana.task_management_api.security.permission.Permissions).PERMISSIONS_DELETE)")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        permissionService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Permission deleted"));
    }

}
