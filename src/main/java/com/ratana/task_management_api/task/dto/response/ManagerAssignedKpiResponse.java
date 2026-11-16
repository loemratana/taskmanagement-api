package com.ratana.task_management_api.task.dto.response;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** KPI + chart data for the tasks the caller assigned (scoped by Task.assignedBy). */
@Builder
public record ManagerAssignedKpiResponse(
        long totalTasks,
        long pendingTasks,
        long todoTasks,
        long reviewTasks,
        long doneTasks,
        long rejectedTasks,
        long overdueTasks,
        List<MemberWorkload> memberWorkload,
        List<DailyCompletion> weeklyCompletions,
        /* Ranked by completionRate desc, then totalTasks desc. */
        List<MemberPerformance> topPerformers
) {

    @Builder
    public record MemberWorkload(UUID memberId, String memberName, String avatarUrl, long taskCount) {
    }

    @Builder
    public record DailyCompletion(LocalDate date, long count) {
    }

    @Builder
    public record MemberPerformance(
            UUID memberId,
            String memberName,
            String avatarUrl,
            String positionName,
            long totalTasks,
            long completedTasks,
            long overdueTasks,
            /* 0-100 */
            double completionRate,
            /* 0-100, share of completed tasks finished on or before their due date */
            double onTimeRate
    ) {
    }
}
