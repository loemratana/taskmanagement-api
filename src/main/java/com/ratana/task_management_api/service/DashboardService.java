package com.ratana.task_management_api.service;

import com.ratana.task_management_api.dto.response.DashboardSummaryResponse;
import com.ratana.task_management_api.dto.response.TeamAnalyticsResponse;

import java.util.UUID;

public interface DashboardService {
    DashboardSummaryResponse getSummary();

    /** managerId's direct reports — see TeamAnalyticsResponse for scope details. */
    TeamAnalyticsResponse getTeamSummary(UUID managerId);
}
