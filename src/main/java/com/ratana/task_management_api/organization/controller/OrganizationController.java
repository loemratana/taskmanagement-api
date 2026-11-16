package com.ratana.task_management_api.organization.controller;

import com.ratana.task_management_api.common.response.ApiResponse;
import com.ratana.task_management_api.organization.dto.response.OrgTreeNodeResponse;
import com.ratana.task_management_api.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/organization")
@Tag(name = "Organization", description = "Reporting hierarchy derived from manager links")
@SecurityRequirement(name = "bearerAuth")
public class OrganizationController {

    private final UserService userService;

    @GetMapping(value = "/tree", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the organization chart",
            description = "One tree per top-level active employee (no manager, or an inactive manager). " +
                    "Built from manager links only — not from positions or departments.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reporting trees")
    @PreAuthorize("hasAuthority('users:view')")
    public ResponseEntity<ApiResponse<List<OrgTreeNodeResponse>>> getTree() {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(userService.getOrganizationTree()));
    }
}
