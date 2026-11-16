package com.ratana.task_management_api.user.controller;


import com.ratana.task_management_api.user.dto.request.AssignManagerRequest;
import com.ratana.task_management_api.user.dto.request.AssignOrgRequest;
import com.ratana.task_management_api.rbac.dto.request.AssignRolesRequest;
import com.ratana.task_management_api.auth.dto.request.ChangePasswordRequest;
import com.ratana.task_management_api.user.dto.request.UserCreateRequest;
import com.ratana.task_management_api.user.dto.request.UserFilterRequest;
import com.ratana.task_management_api.user.dto.request.UserUpdateRequest;
import com.ratana.task_management_api.common.response.ApiResponse;
import com.ratana.task_management_api.auth.dto.response.AuthResponse;
import com.ratana.task_management_api.organization.dto.response.EmployeeHierarchyResponse;
import com.ratana.task_management_api.user.dto.response.ManagerResponse;
import com.ratana.task_management_api.common.pagination.PageResponse;
import com.ratana.task_management_api.user.dto.response.UserPerformanceResponse;
import com.ratana.task_management_api.user.dto.response.UserResponse;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.auth.service.AuthService;
import com.ratana.task_management_api.user.service.UserPerformanceService;
import com.ratana.task_management_api.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User CRUD, search, and assignment operations")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    private final UserPerformanceService userPerformanceService;


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
    @PreAuthorize("hasAuthority('users:create')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody UserCreateRequest request) {

        UserResponse created = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));

    }

    @GetMapping(produces =MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search users", description = "All filter parameters are optional and combine with AND. " +
            "Supports standard Spring pagination/sort params, e.g. ?page=0&size=20&sort=fullName,asc")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of matching users")
    @PreAuthorize("hasAuthority('users:view')")
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
        UserResponse me = userService.getById(principal.getId());
        // Lets clients gate UI by permission (e.g. show the assigned-tasks dashboard to anyone who can assign).
        me.setPermissions(principal.getPermissions());
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(me));
    }

    @PutMapping(value = "/me/password", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Change the current user's password",
            description = "Checks the current password, sets the new one (8-128 characters, different from the current one), "
                    + "signs out every other device, and returns new tokens for this device. The client must replace "
                    + "its stored tokens with the returned ones.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password changed; new tokens returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "New password invalid or unchanged", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Current password is incorrect", content = @Content)
    })
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                authService.changePassword(principal.getId(), request, httpServletRequest)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get users by Id", description = "All filter parameters are optional and combine with AND. " +
            "Supports standard Spring pagination/sort params, e.g. ?page=0&size=20&sort=fullName,asc")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of matching users")
    public ResponseEntity<ApiResponse<UserResponse>> findUserById(@Parameter(description = "id of the user to be searched", required = true) @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(userService.getById(id)));
    }

    @GetMapping(value = "/{id}/performance", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Analyze a user's task performance",
            description = "Task counts, completion/on-time/overdue rates, effort, submission quality, a 0-100 performance "
                    + "score with rating, status and priority breakdowns, a 6-month trend, upcoming and overdue tasks, and "
                    + "insights. Covers active tasks where the user is the primary assignee or a collaborator. "
                    + "Optional from/to (yyyy-MM-dd) filter by task creation date.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Performance analysis"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "'from' is after 'to'", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    @PreAuthorize("#id == principal.id or hasAuthority('users:view')")
    public ResponseEntity<ApiResponse<UserPerformanceResponse>> getUserPerformance(
            @Parameter(description = "User id", required = true) @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(userPerformanceService.getPerformance(id, from, to)));
    }



    @PutMapping(value = "{id}",consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a user's profile", description = "Only non-null fields in the request body are applied.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already in use", content = @Content)
    })
    @PreAuthorize("#id == principal.id or hasAuthority('users:update')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(@Parameter(description = "User id", required = true)
                                                                    @PathVariable UUID id,
                                                                @Valid @RequestBody UserUpdateRequest request) {

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(userService.update(id, request)));


    }

    @GetMapping("/{id}/manager")
    @Operation(summary = "Get a user's direct manager", description = "data is null when the user is top-level.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Manager, or null"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    @PreAuthorize("hasAuthority('users:view')")
    public ResponseEntity<ApiResponse<ManagerResponse>> getManager(
            @Parameter(description = "User id", required = true) @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(userService.getManager(id)));
    }

    @RequestMapping(value = "/{id}/manager", method = {RequestMethod.PUT, RequestMethod.PATCH},
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Assign or clear a user's manager",
            description = "Send managerId: null to make the user top-level. Rejects self-assignment and reporting cycles.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Manager updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Self-assignment, inactive manager, or cycle", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User or manager not found", content = @Content)
    })
    @PreAuthorize("hasAuthority('users:update')")
    public ResponseEntity<ApiResponse<UserResponse>> assignManager(
            @Parameter(description = "User id", required = true) @PathVariable UUID id,
            @Valid @RequestBody AssignManagerRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(userService.assignManager(id, request)));
    }

    @GetMapping("/{id}/reports")
    @Operation(summary = "Get a user's direct reports", description = "Only users whose manager is this user — not the full downline.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Direct reports, sorted by name"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    @PreAuthorize("hasAuthority('users:view')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getDirectReports(
            @Parameter(description = "User id", required = true) @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(userService.getDirectReports(id)));
    }

    @GetMapping("/{id}/hierarchy")
    @Operation(summary = "Get a user's reporting hierarchy",
            description = "Management chain above the user (direct manager first, top-level last) and " +
                    "the full downline below, breadth-first. Derived from manager links only.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hierarchy resolved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    @PreAuthorize("hasAuthority('users:view')")
    public ResponseEntity<ApiResponse<EmployeeHierarchyResponse>> getHierarchy(
            @Parameter(description = "User id", required = true) @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(userService.getHierarchy(id)));
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
    @PreAuthorize("hasAuthority('users:update')")
    public void deactivate(@Parameter(description = "User id", required = true) @PathVariable UUID id) {
        userService.deactivate(id);
    }


    @PatchMapping("/{id}/activate")
    @Operation(summary = "Reactivate a previously deactivated user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "User activated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    @PreAuthorize("hasAuthority('users:update')")
    public ResponseEntity<Void> activate(@Parameter(description = "User id", required = true) @PathVariable UUID id) {
        userService.activate(id);
        return ResponseEntity.noContent().build();
    }


    @PutMapping(value = "/{id}/roles", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace a user's roles", description = "Replaces the entire role set — not additive.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Roles updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "One or more role ids do not exist", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    // Granting roles grants permissions, so only admins may do it.
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> assignRoles(
            @Parameter(description = "User id", required = true) @PathVariable UUID id,
            @Valid @RequestBody AssignRolesRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(userService.assignRoles(id, request)));
    }

    @RequestMapping(value = "/{id}/org", method = {RequestMethod.PUT, RequestMethod.PATCH}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Assign a user's department and position",
            description = "Replaces both values — a null field clears it. If only positionId is given, " +
                    "the department is taken from the position.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Organization updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Inactive department/position, or position not in department", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User, department or position not found", content = @Content)
    })
    @PreAuthorize("hasAuthority('users:update')")
    public ResponseEntity<ApiResponse<UserResponse>> assignOrg(
            @Parameter(description = "User id", required = true) @PathVariable UUID id,
            @Valid @RequestBody AssignOrgRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(userService.assignOrg(id, request)));
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
    @PreAuthorize("#id == principal.id or hasAuthority('users:update')")
    public ResponseEntity<ApiResponse<UserResponse>> uploadAvatar(@PathVariable UUID id , @RequestPart("file") MultipartFile file) {
        UserResponse user = userService.uploadAvatar(id, file);

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(user));
    }
}
