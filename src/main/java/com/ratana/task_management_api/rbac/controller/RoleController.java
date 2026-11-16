package com.ratana.task_management_api.rbac.controller;


import com.ratana.task_management_api.rbac.dto.request.AssignPermissionsRequest;
import com.ratana.task_management_api.rbac.dto.request.RoleRequest;
import com.ratana.task_management_api.common.response.ApiResponse;
import com.ratana.task_management_api.rbac.dto.response.RoleResponse;
import com.ratana.task_management_api.rbac.service.RoleService;
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

@Tag(name = "Roles", description = "Manage roles and their assigned permissions")
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class RoleController {
    private final RoleService roleService;

    //@PreAuthorize("hasAuthority('role:read')")

    @Operation(summary = "List all roles")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(roleService.getAll()));
    }

    @Operation(summary = "Get role details, including its assigned permissions")
    @PreAuthorize("hasAuthority('role:read')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(roleService.getById(id)));
    }

    @Operation(summary = "Create a role")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('role:create')")
    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponse>> create(@Valid @RequestBody RoleRequest request) {
        RoleResponse created = roleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Role created", created));
    }

    @Operation(summary = "Update a role")
    @PreAuthorize("hasAuthority('role:update')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Role updated", roleService.update(id, request)));
    }

    @Operation(summary = "Delete a role", description = "Soft-deletes the role (deactivates it).")
    @PreAuthorize("hasAuthority('role:delete')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        roleService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Role deleted"));
    }

    @Operation(summary = "Assign permissions to a role",
            description = "Replaces the role's entire permission set with the provided list.")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('role:assign_permissions')")
    @PutMapping("/{id}/permissions")
    public ResponseEntity<ApiResponse<RoleResponse>> assignPermissions(
            @PathVariable UUID id, @Valid @RequestBody AssignPermissionsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Permissions assigned", roleService.assignPermissions(id, request)));
    }

}
