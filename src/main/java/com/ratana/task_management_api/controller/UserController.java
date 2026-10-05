package com.ratana.task_management_api.controller;


import com.ratana.task_management_api.dto.request.AssignManagerRequest;
import com.ratana.task_management_api.dto.request.AssignOrgRequest;
import com.ratana.task_management_api.dto.request.AssignRoleRequest;
import com.ratana.task_management_api.dto.request.UserCreateRequest;
import com.ratana.task_management_api.dto.request.UserFilterRequest;
import com.ratana.task_management_api.dto.request.UserUpdateRequest;
import com.ratana.task_management_api.dto.response.ApiResponse;
import com.ratana.task_management_api.dto.response.EmployeeHierarchyResponse;
import com.ratana.task_management_api.dto.response.ManagerResponse;
import com.ratana.task_management_api.dto.response.PageResponse;
import com.ratana.task_management_api.dto.response.UserResponse;
import com.ratana.task_management_api.enums.UserStatus;
import com.ratana.task_management_api.model.User;
import com.ratana.task_management_api.security.UserPrincipal;
import com.ratana.task_management_api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User CRUD, search, and assignment operations")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;


    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a user",description = "Creates a new user account with an encoded password.")
    @ApiResponses(
            {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",description = "User created",
                            content = @Content(schema = @Schema(implementation = UserResponse.class))
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already in use", content = @Content)
            }
    )
    @PreAuthorize("hasAuthority(T(com.ratana.task_management_api.security.permission.Permissions).USERS_CREATE)")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody UserCreateRequest request) {

        UserResponse created = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));

    }

    @GetMapping(produces =MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search users", description = "All filter parameters are optional and combine with AND. " +
            "Supports standard Spring pagination/sort params, e.g. ?page=0&size=20&sort=fullName,asc")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of matching users")
    @PreAuthorize("hasAuthority(T(com.ratana.task_management_api.security.permission.Permissions).USERS_READ)")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> searchUsers(@ParameterObject @ModelAttribute UserFilterRequest fiter,@ParameterObject Pageable pageable)

    {
        PageResponse<UserResponse> page = userService.search(fiter,pageable);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(page));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current authenticated user's profile",
            description = "Resolves the caller from the access token — no id required. Used right after login " +
                    "to populate the client's session, since the login response only contains tokens.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Current user's profile")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(userService.getById(principal.getId())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get users by Id", description = "All filter parameters are optional and combine with AND. " +
            "Supports standard Spring pagination/sort params, e.g. ?page=0&size=20&sort=fullName,asc")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of matching users")
    public ResponseEntity<ApiResponse<UserResponse>> findUserById(@Parameter(description = "id of the user to be searched", required = true) @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(userService.getById(id)));
    }



    @PutMapping(value = "{id}",consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a user's profile", description = "Only non-null fields in the request body are applied.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already in use", content = @Content)
    })
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(@Parameter(description = "User id", required = true)
                                                                    @PathVariable UUID id,
                                                                @Valid @RequestBody UserUpdateRequest request) {

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(userService.update(id, request)));


    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a user (soft delete)",
            description = "Sets is_active = false and status = INACTIVE. No rows are ever hard-deleted, " +
                    "since users are referenced by tasks, approvals, and logs.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "User deactivated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public void deactivate(@Parameter(description = "User id", required = true) @PathVariable UUID id) {
        userService.deactivate(id);
    }


    @PatchMapping("/{id}/activate")
    @Operation(summary = "Reactivate a previously deactivated user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "User activated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<Void> activate(@Parameter(description = "User id", required = true) @PathVariable UUID id) {
        userService.activate(id);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/{id}/manager")
    @Operation(summary = "Get a user's manager", description = "Returns the user they directly report to, or null if they have no manager (top-level).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Manager (or null)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<ManagerResponse>> getManager(@Parameter(description = "User id", required = true) @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(userService.getManager(id)));
    }

    @GetMapping("/{id}/reports")
    @Operation(summary = "Get a user's direct reports", description = "Users whose manager_id points at this user. Not recursive — for the full downline, use the hierarchy endpoint.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Direct reports"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<java.util.List<UserResponse>>> getDirectReports(@Parameter(description = "User id", required = true) @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(userService.getDirectReports(id)));
    }

    @GetMapping("/{id}/hierarchy")
    @Operation(summary = "Get an employee's position in the reporting hierarchy",
            description = "Returns the employee's depth from the top of the org, their management chain (immediate manager " +
                    "up to the root), and every employee under them at any depth — from recursive, single-round-trip queries.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hierarchy info"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<EmployeeHierarchyResponse>> getHierarchy(@Parameter(description = "User id", required = true) @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(userService.getHierarchy(id)));
    }

    @PatchMapping(value = "/{id}/manager", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Assign or change a user's manager",
            description = "Pass managerId: null to clear the manager (making the user top-level). Rejects self-assignment, " +
                    "inactive managers, and assignments that would create a reporting cycle.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Manager assigned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid assignment (self, inactive manager, or would create a cycle)", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User or manager not found", content = @Content)
    })
    @PreAuthorize("hasRole(T(com.ratana.task_management_api.security.role.Roles).ADMIN)")
    public ResponseEntity<ApiResponse<UserResponse>> assignManager(@Parameter(description = "User id", required = true) @PathVariable UUID id,
                                                                     @RequestBody AssignManagerRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(userService.assignManager(id, request)));
    }

    @PatchMapping(value = "/{id}/org", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Assign a user's department and/or position",
            description = "Either field may be omitted to leave it unchanged; at least one must be provided. " +
                    "Does not affect manager_id — use PATCH /{id}/manager for reporting-line changes.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Department/position assigned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Neither departmentId nor positionId provided", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User, department, or position not found", content = @Content)
    })
    @PreAuthorize("hasRole(T(com.ratana.task_management_api.security.role.Roles).ADMIN)")
    public ResponseEntity<ApiResponse<UserResponse>> assignOrg(@Parameter(description = "User id", required = true) @PathVariable UUID id,
                                                                 @RequestBody AssignOrgRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(userService.assignOrg(id, request)));
    }


    @PutMapping(value = "/{id}/role", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace a user's role", description = "Replaces the user's single role assignment.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Role updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Role id does not exist or is inactive", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<UserResponse>> assignRole(
            @Parameter(description = "User id", required = true) @PathVariable UUID id,
            @Valid @RequestBody AssignRoleRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(userService.assignRole(id, request)));
    }



    @PostMapping(
            value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload user avatar", description = "Upload and update user's profile image"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Avatar uploaded successfully"
    )
    public ResponseEntity<ApiResponse<UserResponse>> uploadAvatar(@PathVariable UUID id , @RequestPart("file") MultipartFile file) {
        UserResponse user = userService.uploadAvatar(id, file);

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(user));
    }
}
