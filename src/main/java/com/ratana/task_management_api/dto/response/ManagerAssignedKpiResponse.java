package com.ratana.task_management_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Dashboard KPI + chart data for tasks the caller (a manager) assigned — scoped by
 * assignedBy, not the caller's direct reports, since manager_id hierarchies
 * aren't reliably populated for every manager. Reuses {@link TeamAnalyticsResponse}'s
 * MemberWorkload/DailyCompletion shapes rather than duplicating them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagerAssignedKpiResponse {

    private long totalTasks;
    private long pendingTasks;
    private long todoTasks;
    private long reviewTasks;
    private long doneTasks;
    private long rejectedTasks;
    private long overdueTasks;

    /** Per-assignee task count, among tasks this manager assigned. Sorted by count desc. */
    private List<TeamAnalyticsResponse.MemberWorkload> memberWorkload;
    /** Rolling 7-day window ending today, zero-filled for days with no completions. */
    private List<TeamAnalyticsResponse.DailyCompletion> weeklyCompletions;
    /** Per-assignee performance among tasks this manager assigned. Ranked by completionRate desc, then totalTasks desc. */
    private List<TeamAnalyticsResponse.MemberPerformance> topPerformers;
}
