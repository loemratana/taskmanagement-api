package com.ratana.task_management_api.performance.controller;

import com.ratana.task_management_api.common.response.ApiResponse;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.performance.dto.PerformanceAwardResponse;
import com.ratana.task_management_api.performance.enums.PerformancePeriod;
import com.ratana.task_management_api.performance.service.PerformanceAwardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/performance/awards")
@Tag(name = "Performance awards", description = "Monthly and yearly top-performer announcements (popups)")
@SecurityRequirement(name = "bearerAuth")
public class PerformanceAwardController {

    private final PerformanceAwardService awardService;

    @GetMapping(value = "/pending", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Awards to show as a popup",
            description = "Your department's announcements from the last 45 days that you haven't closed, newest first "
                    + "(at most 3), each with the top 3 and your own place.")
    public ResponseEntity<ApiResponse<List<PerformanceAwardResponse>>> pending(
            @AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.ok(ApiResponse.success(awardService.pending(actor)));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "One announcement with everyone who was rated")
    public ResponseEntity<ApiResponse<PerformanceAwardResponse>> get(
            @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.ok(ApiResponse.success(awardService.get(id, actor)));
    }

    @PostMapping("/{id}/seen")
    @Operation(summary = "Close the popup", description = "The award stops being pending for you. Idempotent.")
    public ResponseEntity<Void> markSeen(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal actor) {
        awardService.markSeen(id, actor);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/publish", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Publish an announcement now (admin)",
            description = "What the scheduler does at 09:00, for a missed run or a test. The period must be finished. "
                    + "Departments that already have this award are skipped.")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> publish(
            @RequestParam PerformancePeriod period,
            @RequestParam int year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer quarter) {
        Integer number = switch (period) {
            case MONTHLY -> month;
            case QUARTERLY -> quarter;
            case YEARLY -> null;
        };
        int published = awardService.publish(period, year, number);
        return ResponseEntity.ok(ApiResponse.success("Published " + published + " award(s)",
                Map.of("published", published)));
    }
}
