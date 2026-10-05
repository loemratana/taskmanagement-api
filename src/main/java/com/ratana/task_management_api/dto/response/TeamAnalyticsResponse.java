package com.ratana.task_management_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate task stats for a manager's team — their direct reports (manager_id-driven,
 * not department membership), computed server-side in one call.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamAnalyticsResponse {

    private long totalMembers;
    private long totalTasks;
    private long activeTasks;
    private long pendingReview;
    private long completedTasks;
    private long overdueTasks;

    /** completedTasks / totalTasks * 100, rounded to one decimal place. 0 when totalTasks is 0. */
    private double completionRate;

    private List<StatusCount> taskStatusBreakdown;
    private List<MemberWorkload> memberWorkload;
    /** Rolling 7-day window ending today, zero-filled for days with no completions. */
    private List<DailyCompletion> weeklyCompletions;
    /** Ranked by completionRate desc, then totalTasks desc. */
    private List<MemberPerformance> topPerformers;

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
    public static class MemberWorkload {
        private UUID memberId;
        private String memberName;
        private String avatarUrl;
        private long taskCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyCompletion {
        private LocalDate date;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberPerformance {
        private UUID memberId;
        private String memberName;
        private String avatarUrl;
        private String positionName;
        private long totalTasks;
        private long completedTasks;
        private long overdueTasks;
        /** completedTasks / totalTasks * 100, rounded to one decimal place. */
        private double completionRate;
        /** Of completed tasks that had a due date, the percent finished on or before it. */
        private double onTimeRate;
    }
}
