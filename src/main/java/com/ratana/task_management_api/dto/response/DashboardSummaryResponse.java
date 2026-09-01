package com.ratana.task_management_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {

    private long totalEmployees;
    private long totalManagers;
    private long totalTasks;
    private long activeTasks;
    private long completedTasks;
    private long pendingApproval;
    private long overdueTasks;

    private List<StatusCount> taskStatusBreakdown;
    private List<DepartmentTaskCount> departmentPerformance;
    private List<TaskResponse> recentTasks;
    private List<ActivityEntry> recentActivities;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusCount {
        private String status;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepartmentTaskCount {
        private String department;
        private long count;
    }

    /**
     * No audit-log table is populated (TaskLog exists but nothing writes to
     * it) — these are synthesized from real task creation/update timestamps,
     * not a true activity log.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityEntry {
        private String user;
        private String action;
        private Instant time;
    }
}
