package com.ratana.task_management_api.controller;

import com.ratana.task_management_api.dto.response.ApiResponse;
import com.ratana.task_management_api.dto.response.DashboardSummaryResponse;
import com.ratana.task_management_api.dto.response.TeamAnalyticsResponse;
import com.ratana.task_management_api.security.UserPrincipal;
import com.ratana.task_management_api.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Aggregate stats for the admin dashboard")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(
            summary = "Get admin dashboard summary",
            description = "Real-time aggregate stats (task counts, status/department breakdowns, " +
                    "recent tasks, and a synthesized recent-activity feed) computed server-side in one call."
    )
    @PreAuthorize("hasRole(T(com.ratana.task_management_api.security.role.Roles).ADMIN)")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getSummary()));
    }

    @Operation(
            summary = "Get the caller's team analytics",
            description = "Aggregate task stats for the caller's direct reports (manager_id-driven, not department " +
                    "membership): status breakdown, per-member workload, a rolling 7-day completion trend, and a " +
                    "completion-rate-ranked performer list. Computed server-side in one call."
    )
    @PreAuthorize("hasAnyAuthority(T(com.ratana.task_management_api.security.permission.Permissions).TASK_VIEW_TEAM,T(com.ratana.task_management_api.security.permission.Permissions).TASK_ASSIGN_TEAM)")
    @GetMapping("/team-summary")
    public ResponseEntity<ApiResponse<TeamAnalyticsResponse>> getTeamSummary(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getTeamSummary(principal.getId())));
    }
}
