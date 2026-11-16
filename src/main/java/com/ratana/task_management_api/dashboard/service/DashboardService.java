package com.ratana.task_management_api.dashboard.service;

import com.ratana.task_management_api.dashboard.dto.request.DashboardFilter;
import com.ratana.task_management_api.dashboard.dto.response.DashboardSummaryResponse;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;


public interface DashboardService {
    DashboardSummaryResponse getSummary(UserPrincipal principal , DashboardFilter filter);

}
