package com.ratana.task_management_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Employee-facing KPI derived purely from the caller's own assigned tasks —
 * a completion rate, not a manager-assigned score or a cross-employee rank.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiSummaryResponse {

    private long totalTasks;
    private long completedTasks;
    private long inProgressTasks;
    private long overdueTasks;

    /** completedTasks / totalTasks * 100, rounded to one decimal place. 0 when totalTasks is 0. */
    private double kpi;
}
