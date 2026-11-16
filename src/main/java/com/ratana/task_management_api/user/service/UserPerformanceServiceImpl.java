package com.ratana.task_management_api.user.service;

import com.ratana.task_management_api.user.dto.response.UserPerformanceResponse;
import com.ratana.task_management_api.task.enums.Priority;
import com.ratana.task_management_api.task.enums.SubmissionStatus;
import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.common.exception.BadRequestException;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.entity.TaskSubmission;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.task.repository.TaskRepository;
import com.ratana.task_management_api.task.repository.TaskSubmissionRepository;
import com.ratana.task_management_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserPerformanceServiceImpl implements UserPerformanceService {

    private static final Set<TaskStatus> COMPLETED = EnumSet.of(TaskStatus.DONE, TaskStatus.APPROVED);
    private static final Set<TaskStatus> FINISHED = EnumSet.of(TaskStatus.DONE, TaskStatus.APPROVED, TaskStatus.CANCELLED);
    private static final int TREND_MONTHS = 6;
    private static final int UPCOMING_DAYS = 7;
    private static final int TASK_LIST_LIMIT = 10;

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;

    private ZoneId reportZone = ZoneId.of("Asia/Phnom_Penh");

    @Value("${app.reminder.default-timezone:Asia/Phnom_Penh}")
    void setReportZone(String zone) {
        this.reportZone = ZoneId.of(zone);
    }

    @Override
    @Transactional(readOnly = true)
    public UserPerformanceResponse getPerformance(UUID userId, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("'from' must be on or before 'to'");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        LocalDate today = LocalDate.now(reportZone);

        List<Task> tasks = taskRepository.findActiveByParticipant(userId).stream()
                .filter(task -> inRange(createdDate(task), from, to))
                .toList();
        Set<UUID> taskIds = tasks.stream().map(Task::getId).collect(Collectors.toSet());
        List<TaskSubmission> submissions = taskSubmissionRepository.findAllBySubmittedById(userId).stream()
                .filter(submission -> taskIds.contains(submission.getTask().getId()))
                .toList();

        // Task has no completedAt: prefer the review time of the user's approved submission, else the task's last update.
        Map<UUID, Instant> approvedAt = submissions.stream()
                .filter(submission -> submission.getStatus() == SubmissionStatus.APPROVED && submission.getReviewedAt() != null)
                .collect(Collectors.toMap(submission -> submission.getTask().getId(), TaskSubmission::getReviewedAt,
                        (a, b) -> a.isAfter(b) ? a : b));

        List<Task> completed = tasks.stream().filter(task -> COMPLETED.contains(task.getStatus())).toList();
        long completedOnTime = completed.stream().filter(task -> isOnTime(task, approvedAt)).count();
        List<Task> overdue = tasks.stream().filter(task -> isOverdue(task, today)).toList();
        long cancelled = countStatus(tasks, TaskStatus.CANCELLED);
        long active = tasks.stream().filter(task -> !FINISHED.contains(task.getStatus())).count();

        UserPerformanceResponse.TaskSummary summary = UserPerformanceResponse.TaskSummary.builder()
                .total(tasks.size())
                .active(active)
                .completed(completed.size())
                .todo(countStatus(tasks, TaskStatus.TODO))
                .inProgress(countStatus(tasks, TaskStatus.IN_PROGRESS))
                .inReview(countStatus(tasks, TaskStatus.IN_REVIEW))
                .rejected(countStatus(tasks, TaskStatus.REJECTED))
                .blocked(countStatus(tasks, TaskStatus.BLOCKED))
                .cancelled(cancelled)
                .overdue(overdue.size())
                .completedOnTime(completedOnTime)
                .completedLate(completed.size() - completedOnTime)
                .build();

        UserPerformanceResponse.Rates rates = UserPerformanceResponse.Rates.builder()
                .completionRate(percent(completed.size(), tasks.size() - cancelled))
                .onTimeRate(percent(completedOnTime, completed.size()))
                .overdueRate(percent(overdue.size(), active))
                .build();

        UserPerformanceResponse.SubmissionStats submissionStats = buildSubmissionStats(submissions);
        double score = tasks.isEmpty() ? 0 : Math.round((rates.completionRate() * 0.4
                + rates.onTimeRate() * 0.3
                + (submissions.isEmpty() ? rates.onTimeRate() : submissionStats.firstPassApprovalRate()) * 0.3) * 10) / 10.0;

        log.debug("Performance for user {}: {} task(s), {} submission(s), score {}", userId, tasks.size(), submissions.size(), score);
        return UserPerformanceResponse.builder()
                .user(UserPerformanceResponse.UserInfo.builder()
                        .id(user.getId())
                        .fullName(user.getFullName())
                        .email(user.getEmail())
                        .avatarUrl(user.getAvatarUrl())
                        .positionName(user.getPosition() != null ? user.getPosition().getName() : null)
                        .departmentName(user.getDepartment() != null ? user.getDepartment().getName() : null)
                        .build())
                .from(from)
                .to(to)
                .tasks(summary)
                .rates(rates)
                .effort(buildEffort(completed, approvedAt))
                .submissions(submissionStats)
                .performanceScore(score)
                .rating(rating(tasks.isEmpty(), score))
                .statusBreakdown(Arrays.stream(TaskStatus.values())
                        .map(status -> new UserPerformanceResponse.StatusCount(status, countStatus(tasks, status)))
                        .toList())
                .priorityBreakdown(buildPriorityBreakdown(tasks, today))
                .monthlyTrend(buildMonthlyTrend(tasks, approvedAt, today))
                .upcomingDeadlines(tasks.stream()
                        .filter(task -> !FINISHED.contains(task.getStatus()) && task.getDueDate() != null
                                && !task.getDueDate().isBefore(today) && !task.getDueDate().isAfter(today.plusDays(UPCOMING_DAYS)))
                        .sorted(Comparator.comparing(Task::getDueDate))
                        .limit(TASK_LIST_LIMIT)
                        .map(task -> toBrief(task, today))
                        .toList())
                .overdueTasks(overdue.stream()
                        .sorted(Comparator.comparing(Task::getDueDate))
                        .limit(TASK_LIST_LIMIT)
                        .map(task -> toBrief(task, today))
                        .toList())
                .insights(buildInsights(summary, rates, submissionStats))
                .build();
    }

    private UserPerformanceResponse.SubmissionStats buildSubmissionStats(List<TaskSubmission> submissions) {
        long approved = submissions.stream().filter(s -> s.getStatus() == SubmissionStatus.APPROVED).count();
        long rejected = submissions.stream().filter(s -> s.getStatus() == SubmissionStatus.REJECTED).count();

        Map<UUID, List<TaskSubmission>> byTask = submissions.stream()
                .collect(Collectors.groupingBy(s -> s.getTask().getId()));
        long firstPass = byTask.values().stream()
                .filter(rounds -> rounds.stream().anyMatch(s -> s.getRound() == 1 && s.getStatus() == SubmissionStatus.APPROVED))
                .count();
        double avgRounds = byTask.values().stream()
                .mapToInt(rounds -> rounds.stream().mapToInt(TaskSubmission::getRound).max().orElse(0))
                .average().orElse(0);

        return UserPerformanceResponse.SubmissionStats.builder()
                .total(submissions.size())
                .approved(approved)
                .rejected(rejected)
                .pending(submissions.size() - approved - rejected)
                .approvalRate(percent(approved, approved + rejected))
                .firstPassApprovalRate(percent(firstPass, byTask.size()))
                .avgRoundsPerTask(Math.round(avgRounds * 10) / 10.0)
                .build();
    }

    private UserPerformanceResponse.Effort buildEffort(List<Task> completed, Map<UUID, Instant> approvedAt) {
        OptionalDouble avgDays = completed.stream()
                .mapToLong(task -> {
                    LocalDate start = task.getStartDate() != null ? task.getStartDate() : createdDate(task);
                    return Math.max(0, ChronoUnit.DAYS.between(start, completionDate(task, approvedAt)));
                })
                .average();

        BigDecimal estimated = BigDecimal.ZERO;
        BigDecimal actual = BigDecimal.ZERO;
        BigDecimal pairedEstimated = BigDecimal.ZERO;
        BigDecimal pairedActual = BigDecimal.ZERO;
        for (Task task : completed) {
            if (task.getEstimatedHours() != null) {
                estimated = estimated.add(task.getEstimatedHours());
            }
            if (task.getActualHours() != null) {
                actual = actual.add(task.getActualHours());
            }
            if (task.getEstimatedHours() != null && task.getActualHours() != null) {
                pairedEstimated = pairedEstimated.add(task.getEstimatedHours());
                pairedActual = pairedActual.add(task.getActualHours());
            }
        }

        return UserPerformanceResponse.Effort.builder()
                .avgCompletionDays(avgDays.isPresent() ? Math.round(avgDays.getAsDouble() * 10) / 10.0 : null)
                .totalEstimatedHours(estimated)
                .totalActualHours(actual)
                .estimateAccuracy(pairedEstimated.signum() == 0 ? null
                        : pairedActual.multiply(BigDecimal.valueOf(100))
                        .divide(pairedEstimated, 1, RoundingMode.HALF_UP).doubleValue())
                .build();
    }

    private List<UserPerformanceResponse.PriorityStats> buildPriorityBreakdown(List<Task> tasks, LocalDate today) {
        List<UserPerformanceResponse.PriorityStats> breakdown = new ArrayList<>();
        for (Priority priority : Priority.values()) {
            List<Task> ofPriority = tasks.stream().filter(task -> task.getPriority() == priority).toList();
            long completed = ofPriority.stream().filter(task -> COMPLETED.contains(task.getStatus())).count();
            long cancelled = countStatus(ofPriority, TaskStatus.CANCELLED);
            breakdown.add(UserPerformanceResponse.PriorityStats.builder()
                    .priority(priority)
                    .total(ofPriority.size())
                    .completed(completed)
                    .overdue(ofPriority.stream().filter(task -> isOverdue(task, today)).count())
                    .completionRate(percent(completed, ofPriority.size() - cancelled))
                    .build());
        }
        return breakdown;
    }

    private List<UserPerformanceResponse.MonthlyTrend> buildMonthlyTrend(List<Task> tasks, Map<UUID, Instant> approvedAt,
                                                                         LocalDate today) {
        Map<YearMonth, Long> assigned = tasks.stream()
                .collect(Collectors.groupingBy(task -> YearMonth.from(createdDate(task)), Collectors.counting()));
        Map<YearMonth, List<Task>> completedByMonth = tasks.stream()
                .filter(task -> COMPLETED.contains(task.getStatus()))
                .collect(Collectors.groupingBy(task -> YearMonth.from(completionDate(task, approvedAt))));

        List<UserPerformanceResponse.MonthlyTrend> trend = new ArrayList<>();
        YearMonth current = YearMonth.from(today);
        for (int monthsAgo = TREND_MONTHS - 1; monthsAgo >= 0; monthsAgo--) {
            YearMonth month = current.minusMonths(monthsAgo);
            List<Task> done = completedByMonth.getOrDefault(month, List.of());
            trend.add(UserPerformanceResponse.MonthlyTrend.builder()
                    .month(month)
                    .assigned(assigned.getOrDefault(month, 0L))
                    .completed(done.size())
                    .completedOnTime(done.stream().filter(task -> isOnTime(task, approvedAt)).count())
                    .build());
        }
        return trend;
    }

    private static List<String> buildInsights(UserPerformanceResponse.TaskSummary summary,
                                              UserPerformanceResponse.Rates rates,
                                              UserPerformanceResponse.SubmissionStats submissions) {
        List<String> insights = new ArrayList<>();
        if (summary.total() == 0) {
            insights.add("No tasks assigned in this period.");
            return insights;
        }
        if (summary.completed() > 0 && rates.onTimeRate() >= 90) {
            insights.add("Consistently delivers on time (" + rates.onTimeRate() + "% of completed tasks).");
        } else if (summary.completedLate() > 0 && rates.onTimeRate() < 60) {
            insights.add(summary.completedLate() + " task(s) were completed after their due date.");
        }
        if (summary.overdue() > 0) {
            insights.add(summary.overdue() + " unfinished task(s) are past their due date.");
        }
        if (summary.blocked() > 0) {
            insights.add(summary.blocked() + " task(s) are blocked and may need support.");
        }
        if (summary.active() >= 10) {
            insights.add("High current workload: " + summary.active() + " active task(s).");
        }
        if (submissions.total() > 0 && submissions.firstPassApprovalRate() >= 80) {
            insights.add("High work quality: " + submissions.firstPassApprovalRate() + "% of submissions approved on the first round.");
        } else if (submissions.rejected() > 0 && submissions.avgRoundsPerTask() >= 2) {
            insights.add("Submissions often need rework (avg " + submissions.avgRoundsPerTask() + " rounds per task).");
        }
        if (summary.inReview() > 0) {
            insights.add(summary.inReview() + " task(s) are waiting for review.");
        }
        return insights;
    }

    private UserPerformanceResponse.TaskBrief toBrief(Task task, LocalDate today) {
        return UserPerformanceResponse.TaskBrief.builder()
                .id(task.getId())
                .title(task.getTitle())
                .status(task.getStatus())
                .priority(task.getPriority())
                .dueDate(task.getDueDate())
                .progress(task.computeProgress())
                .daysUntilDue(ChronoUnit.DAYS.between(today, task.getDueDate()))
                .build();
    }

    private boolean isOnTime(Task task, Map<UUID, Instant> approvedAt) {
        return task.getDueDate() == null || !completionDate(task, approvedAt).isAfter(task.getDueDate());
    }

    private LocalDate completionDate(Task task, Map<UUID, Instant> approvedAt) {
        // Stored completion time first; older rows fall back to the approval review time, then the last update.
        Instant at = task.getCompletedAt() != null
                ? task.getCompletedAt()
                : approvedAt.getOrDefault(task.getId(), task.getUpdatedAt());
        return at != null ? at.atZone(reportZone).toLocalDate() : createdDate(task);
    }

    private LocalDate createdDate(Task task) {
        return task.getCreatedAt() != null ? task.getCreatedAt().atZone(reportZone).toLocalDate() : LocalDate.now(reportZone);
    }

    private static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    private static boolean isOverdue(Task task, LocalDate today) {
        return task.getDueDate() != null && task.getDueDate().isBefore(today) && !FINISHED.contains(task.getStatus());
    }

    private static long countStatus(List<Task> tasks, TaskStatus status) {
        return tasks.stream().filter(task -> task.getStatus() == status).count();
    }

    private static String rating(boolean noData, double score) {
        if (noData) return "NO_DATA";
        if (score >= 85) return "EXCELLENT";
        if (score >= 70) return "GOOD";
        if (score >= 50) return "AVERAGE";
        return "NEEDS_IMPROVEMENT";
    }

    /** 0-100, one decimal place; 0 when there is nothing to divide by. */
    private static double percent(long part, long whole) {
        return whole <= 0 ? 0 : Math.round(part * 1000.0 / whole) / 10.0;
    }
}
