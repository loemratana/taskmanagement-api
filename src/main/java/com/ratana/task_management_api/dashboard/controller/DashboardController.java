package com.ratana.task_management_api.dashboard.controller;

import com.ratana.task_management_api.dashboard.dto.request.DashboardFilter;
import com.ratana.task_management_api.common.response.ApiResponse;
import com.ratana.task_management_api.dashboard.dto.response.DashboardSummaryResponse;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

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
                    "recent tasks, and a synthesized recent-activity feed) computed server-side in one call. " +
                    "Optional filters: managerId (tasks that user assigned), employeeId (tasks assigned to that user), " +
                    "departmentId, and a createdAt date range. Every count, overdue included, respects the filters."
    )
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getSummary(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID managerId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate

            )
    {

        DashboardFilter filter = DashboardFilter.builder()
                .managerId(managerId)
                .departmentId(departmentId)
                .employeeId(employeeId)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        return ResponseEntity.ok(ApiResponse.success(dashboardService.getSummary(principal,filter)));
    }
}
