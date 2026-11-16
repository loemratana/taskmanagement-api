package com.ratana.task_management_api.dashboard.dto.response;

public interface DashboardTaskStatistics {
    Long getTotalTasks();

    Long getActiveTasks();

    Long getCompletedTasks();

    Long getPendingApproval();

    Long getOverdueTasks();
}
