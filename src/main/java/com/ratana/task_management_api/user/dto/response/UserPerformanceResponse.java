package com.ratana.task_management_api.user.dto.response;

import com.ratana.task_management_api.task.enums.Priority;
import com.ratana.task_management_api.task.enums.TaskStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** Task performance analysis for one user, over tasks they are the primary assignee of or collaborate on. */
@Builder
public record UserPerformanceResponse(
        UserInfo user,
        /* Inclusive range on task creation date; null bounds mean "unbounded". */
        LocalDate from,
        LocalDate to,
        TaskSummary tasks,
        Rates rates,
        Effort effort,
        SubmissionStats submissions,
        /* 0-100: 40% completion rate, 30% on-time rate, 30% first-pass approval rate. */
        double performanceScore,
        /* EXCELLENT (>=85), GOOD (>=70), AVERAGE (>=50), NEEDS_IMPROVEMENT, or NO_DATA when there are no tasks. */
        String rating,
        List<StatusCount> statusBreakdown,
        List<PriorityStats> priorityBreakdown,
        /* Last 6 months, oldest first. */
        List<MonthlyTrend> monthlyTrend,
        /* Unfinished tasks due in the next 7 days, soonest first. */
        List<TaskBrief> upcomingDeadlines,
        /* Unfinished tasks past their due date, most overdue first. */
        List<TaskBrief> overdueTasks,
        /* Plain-language observations derived from the numbers above. */
        List<String> insights
) {

    @Builder
    public record UserInfo(UUID id, String fullName, String email, String avatarUrl,
                           String positionName, String departmentName) {
    }

    @Builder
    public record TaskSummary(
            long total,
            /* Not DONE/APPROVED/CANCELLED. */
            long active,
            /* DONE or APPROVED. */
            long completed,
            long todo,
            long inProgress,
            long inReview,
            long rejected,
            long blocked,
            long cancelled,
            long overdue,
            long completedOnTime,
            long completedLate
    ) {
    }

    /** All rates are 0-100 with one decimal place. */
    @Builder
    public record Rates(
            /* completed / (total - cancelled) */
            double completionRate,
            /* completed on or before due date / completed */
            double onTimeRate,
            /* overdue / active */
            double overdueRate
    ) {
    }

    @Builder
    public record Effort(
            /* Average days from start date (or creation) to completion, over completed tasks. */
            Double avgCompletionDays,
            BigDecimal totalEstimatedHours,
            BigDecimal totalActualHours,
            /* actual / estimated * 100 over tasks that have both; >100 means over estimate. */
            Double estimateAccuracy
    ) {
    }

    @Builder
    public record SubmissionStats(
            long total,
            long approved,
            long rejected,
            long pending,
            /* approved / reviewed */
            double approvalRate,
            /* Share of submitted tasks approved on round 1. */
            double firstPassApprovalRate,
            /* Average number of submission rounds per submitted task. */
            double avgRoundsPerTask
    ) {
    }

    @Builder
    public record StatusCount(TaskStatus status, long count) {
    }

    @Builder
    public record PriorityStats(Priority priority, long total, long completed, long overdue, double completionRate) {
    }

    @Builder
    public record MonthlyTrend(YearMonth month, long assigned, long completed, long completedOnTime) {
    }

    @Builder
    public record TaskBrief(UUID id, String title, TaskStatus status, Priority priority,
                            LocalDate dueDate, int progress,
                            /* Negative when overdue. */
                            long daysUntilDue) {
    }
}
