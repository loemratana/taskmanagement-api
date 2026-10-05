package com.ratana.task_management_api.controller;

import com.ratana.task_management_api.dto.response.ApiResponse;
import com.ratana.task_management_api.dto.response.OrganizationTreeNodeResponse;
import com.ratana.task_management_api.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/organization")
@Tag(name = "Organization", description = "Company-wide reporting hierarchy, derived from manager_id — independent of position or department")
@SecurityRequirement(name = "bearerAuth")
public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping(value = "/tree", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the complete organization tree",
            description = "Rooted at every top-level employee (manager_id IS NULL), with their full downline nested underneath. " +
                    "Built from a single recursive query — no N+1 regardless of org size.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Organization tree")
    public ResponseEntity<ApiResponse<List<OrganizationTreeNodeResponse>>> getTree() {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(organizationService.getTree()));
    }
}
