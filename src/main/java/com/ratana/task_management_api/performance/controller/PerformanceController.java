package com.ratana.task_management_api.performance.controller;

import com.ratana.task_management_api.common.response.ApiResponse;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.performance.dto.PerformanceLeaderboardResponse;
import com.ratana.task_management_api.performance.enums.PerformancePeriod;
import com.ratana.task_management_api.performance.service.PerformanceLeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/performance")
@Tag(name = "Performance", description = "Department top performers and star ratings")
@SecurityRequirement(name = "bearerAuth")
public class PerformanceController {

    private final PerformanceLeaderboardService leaderboardService;

    @GetMapping(value = "/leaderboard", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Department top performers for a month or quarter",
            description = "Open to every signed-in user. Score 0-100 = on time 30% + quality (rejections) 25% "
                    + "+ completion 20% + overdue 15% + speed 10%, weighted by task priority and blended with the "
                    + "department average for people with few tasks. Stars 1-5 in half steps. People with fewer than "
                    + "minTasksForRating counted tasks are listed unrated at the end.")
    public ResponseEntity<ApiResponse<PerformanceLeaderboardResponse>> leaderboard(
            @Parameter(description = "Default: your own department") @RequestParam(required = false) UUID departmentId,
            @Parameter(description = "MONTHLY (default), QUARTERLY or YEARLY")
            @RequestParam(defaultValue = "MONTHLY") PerformancePeriod period,
            @Parameter(description = "Default: current year") @RequestParam(required = false) Integer year,
            @Parameter(description = "1-12, for MONTHLY. Default: current month") @RequestParam(required = false) Integer month,
            @Parameter(description = "1-4, for QUARTERLY. Default: current quarter")
            @RequestParam(required = false) Integer quarter,
            @Parameter(description = "Return only the first N entries (e.g. 10 for a top-10)")
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal UserPrincipal actor) {
        Integer number = switch (period) {
            case MONTHLY -> month;
            case QUARTERLY -> quarter;
            case YEARLY -> null;
        };
        return ResponseEntity.ok(ApiResponse.success(
                leaderboardService.leaderboard(departmentId, period, year, number, limit, actor)));
    }
}
