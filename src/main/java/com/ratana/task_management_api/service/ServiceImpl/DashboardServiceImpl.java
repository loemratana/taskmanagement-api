package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.dto.response.DashboardSummaryResponse;
import com.ratana.task_management_api.dto.response.TeamAnalyticsResponse;
import com.ratana.task_management_api.enums.TaskStatus;
import com.ratana.task_management_api.mapper.TaskMapper;
import com.ratana.task_management_api.model.Department;
import com.ratana.task_management_api.model.Task;
import com.ratana.task_management_api.model.User;
import com.ratana.task_management_api.repository.DepartmentRepository;
import com.ratana.task_management_api.repository.TaskRepository;
import com.ratana.task_management_api.repository.UserRepository;
import com.ratana.task_management_api.security.role.Roles;
import com.ratana.task_management_api.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final List<TaskStatus> FINISHED_STATUSES =
            List.of(TaskStatus.DONE, TaskStatus.APPROVED, TaskStatus.CANCELLED);

    private static final List<TaskStatus> STATUS_BREAKDOWN_ORDER = List.of(
            TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW,
            TaskStatus.DONE, TaskStatus.REJECTED
    );

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final TaskMapper taskMapper;

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        long totalTasks = taskRepository.count();
        long activeTasks = taskRepository.countByStatus(TaskStatus.IN_PROGRESS);
        long completedTasks = taskRepository.countByStatus(TaskStatus.DONE);
        long pendingApproval = taskRepository.countByStatus(TaskStatus.IN_REVIEW);
        long overdueTasks = taskRepository.countOverdue(FINISHED_STATUSES);

        long totalEmployees = userRepository.countByRole_NameIgnoreCase(Roles.EMPLOYEE);
        long totalManagers = userRepository.countByRole_NameIgnoreCase(Roles.MANAGER);

        List<Task> recent = taskRepository.findTop20ByOrderByCreatedAtDesc();

        return DashboardSummaryResponse.builder()
                .totalEmployees(totalEmployees)
                .totalManagers(totalManagers)
                .totalTasks(totalTasks)
                .activeTasks(activeTasks)
                .completedTasks(completedTasks)
                .pendingApproval(pendingApproval)
                .overdueTasks(overdueTasks)
                .taskStatusBreakdown(buildStatusBreakdown())
                .departmentPerformance(buildDepartmentPerformance())
                .recentTasks(taskMapper.toListResponse(recent.stream().limit(10).toList()))
                .recentActivities(buildRecentActivities(recent))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TeamAnalyticsResponse getTeamSummary(UUID managerId) {
        List<User> members = userRepository.findUserByManagerId(managerId);

        if (members.isEmpty()) {
            return TeamAnalyticsResponse.builder()
                    .totalMembers(0)
                    .taskStatusBreakdown(buildTeamStatusBreakdown(Map.of()))
                    .memberWorkload(List.of())
                    .weeklyCompletions(buildWeeklyCompletions(List.of()))
                    .topPerformers(List.of())
                    .build();
        }

        List<UUID> memberIds = members.stream().map(User::getId).toList();
        List<Task> teamTasks = taskRepository.findByAssigneeIdInAndActiveTrue(memberIds);

        Map<TaskStatus, Long> statusCounts = teamTasks.stream()
                .collect(Collectors.groupingBy(Task::getStatus, Collectors.counting()));

        LocalDate today = LocalDate.now();
        long totalTasks = teamTasks.size();
        long activeTasks = statusCounts.getOrDefault(TaskStatus.IN_PROGRESS, 0L);
        long pendingReview = statusCounts.getOrDefault(TaskStatus.IN_REVIEW, 0L);
        long completedTasks = statusCounts.getOrDefault(TaskStatus.DONE, 0L);
        long overdueTasks = teamTasks.stream()
                .filter(task -> isOverdue(task, today))
                .count();

        Map<UUID, List<Task>> tasksByMember = teamTasks.stream()
                .collect(Collectors.groupingBy(task -> task.getAssignee().getId()));

        return TeamAnalyticsResponse.builder()
                .totalMembers(members.size())
                .totalTasks(totalTasks)
                .activeTasks(activeTasks)
                .pendingReview(pendingReview)
                .completedTasks(completedTasks)
                .overdueTasks(overdueTasks)
                .completionRate(rate(completedTasks, totalTasks))
                .taskStatusBreakdown(buildTeamStatusBreakdown(statusCounts))
                .memberWorkload(buildMemberWorkload(members, tasksByMember))
                .weeklyCompletions(buildWeeklyCompletions(teamTasks))
                .topPerformers(buildTopPerformers(members, tasksByMember, today))
                .build();
    }

    private boolean isOverdue(Task task, LocalDate today) {
        return task.getDueDate() != null
                && task.getDueDate().isBefore(today)
                && !FINISHED_STATUSES.contains(task.getStatus());
    }

    private List<TeamAnalyticsResponse.StatusCount> buildTeamStatusBreakdown(Map<TaskStatus, Long> statusCounts) {
        List<TeamAnalyticsResponse.StatusCount> breakdown = new ArrayList<>();
        for (TaskStatus status : STATUS_BREAKDOWN_ORDER) {
            breakdown.add(TeamAnalyticsResponse.StatusCount.builder()
                    .status(status.name())
                    .count(statusCounts.getOrDefault(status, 0L))
                    .build());
        }
        return breakdown;
    }

    private List<TeamAnalyticsResponse.MemberWorkload> buildMemberWorkload(
            List<User> members, Map<UUID, List<Task>> tasksByMember) {
        return members.stream()
                .map(member -> TeamAnalyticsResponse.MemberWorkload.builder()
                        .memberId(member.getId())
                        .memberName(member.getFullName())
                        .avatarUrl(member.getAvatarUrl())
                        .taskCount(tasksByMember.getOrDefault(member.getId(), List.of()).size())
                        .build())
                .toList();
    }

    /** Rolling 7-day window ending today, zero-filled for days with no completions. */
    private List<TeamAnalyticsResponse.DailyCompletion> buildWeeklyCompletions(List<Task> teamTasks) {
        LocalDate today = LocalDate.now();
        LocalDate windowStart = today.minusDays(6);

        Map<LocalDate, Long> countsByDay = teamTasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.DONE && task.getCompletedAt() != null)
                .map(task -> task.getCompletedAt().atZone(ZoneId.systemDefault()).toLocalDate())
                .filter(date -> !date.isBefore(windowStart) && !date.isAfter(today))
                .collect(Collectors.groupingBy(date -> date, Collectors.counting()));

        List<TeamAnalyticsResponse.DailyCompletion> days = new ArrayList<>();
        for (LocalDate date = windowStart; !date.isAfter(today); date = date.plusDays(1)) {
            days.add(TeamAnalyticsResponse.DailyCompletion.builder()
                    .date(date)
                    .count(countsByDay.getOrDefault(date, 0L))
                    .build());
        }
        return days;
    }

    private List<TeamAnalyticsResponse.MemberPerformance> buildTopPerformers(
            List<User> members, Map<UUID, List<Task>> tasksByMember, LocalDate today) {
        return members.stream()
                .map(member -> {
                    List<Task> memberTasks = tasksByMember.getOrDefault(member.getId(), List.of());
                    long total = memberTasks.size();
                    long completed = memberTasks.stream()
                            .filter(task -> task.getStatus() == TaskStatus.DONE)
                            .count();
                    long overdue = memberTasks.stream().filter(task -> isOverdue(task, today)).count();

                    List<Task> onTimeEligible = memberTasks.stream()
                            .filter(task -> task.getStatus() == TaskStatus.DONE
                                    && task.getDueDate() != null
                                    && task.getCompletedAt() != null)
                            .toList();
                    long onTime = onTimeEligible.stream()
                            .filter(task -> !task.getCompletedAt().atZone(ZoneId.systemDefault()).toLocalDate()
                                    .isAfter(task.getDueDate()))
                            .count();

                    return TeamAnalyticsResponse.MemberPerformance.builder()
                            .memberId(member.getId())
                            .memberName(member.getFullName())
                            .avatarUrl(member.getAvatarUrl())
                            .positionName(member.getPosition() == null ? null : member.getPosition().getName())
                            .totalTasks(total)
                            .completedTasks(completed)
                            .overdueTasks(overdue)
                            .completionRate(rate(completed, total))
                            .onTimeRate(rate(onTime, onTimeEligible.size()))
                            .build();
                })
                .sorted(Comparator
                        .comparingDouble(TeamAnalyticsResponse.MemberPerformance::getCompletionRate).reversed()
                        .thenComparing(Comparator.comparingLong(TeamAnalyticsResponse.MemberPerformance::getTotalTasks).reversed()))
                .limit(10)
                .toList();
    }

    private double rate(long numerator, long denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return Math.round((numerator * 1000.0) / denominator) / 10.0;
    }

    private List<DashboardSummaryResponse.StatusCount> buildStatusBreakdown() {
        Map<TaskStatus, Long> counts = new LinkedHashMap<>();
        for (Object[] row : taskRepository.countGroupedByStatus()) {
            counts.put((TaskStatus) row[0], (Long) row[1]);
        }

        List<DashboardSummaryResponse.StatusCount> breakdown = new ArrayList<>();
        for (TaskStatus status : STATUS_BREAKDOWN_ORDER) {
            breakdown.add(DashboardSummaryResponse.StatusCount.builder()
                    .status(status.name())
                    .count(counts.getOrDefault(status, 0L))
                    .build());
        }
        return breakdown;
    }

    private List<DashboardSummaryResponse.DepartmentTaskCount> buildDepartmentPerformance() {
        Map<UUID, Long> counts = new LinkedHashMap<>();
        for (Object[] row : taskRepository.countGroupedByDepartment()) {
            counts.put((UUID) row[0], (Long) row[1]);
        }

        List<DashboardSummaryResponse.DepartmentTaskCount> performance = new ArrayList<>();
        for (Department department : departmentRepository.findAllByOrderByNameAsc()) {
            if (!department.isActive()) {
                continue;
            }
            performance.add(DashboardSummaryResponse.DepartmentTaskCount.builder()
                    .department(department.getName())
                    .count(counts.getOrDefault(department.getId(), 0L))
                    .build());
        }
        return performance;
    }

    /**
     * No populated audit log exists (TaskLog has no writer) — derives
     * "created" and "completed" entries from real task creator/assignee and
     * timestamps instead of a true activity log.
     */
    private List<DashboardSummaryResponse.ActivityEntry> buildRecentActivities(List<Task> recent) {
        List<DashboardSummaryResponse.ActivityEntry> entries = new ArrayList<>();

        for (Task task : recent) {
            if (task.getCreator() != null) {
                entries.add(DashboardSummaryResponse.ActivityEntry.builder()
                        .user(task.getCreator().getFullName())
                        .action("created a task \"" + task.getTitle() + "\"")
                        .time(task.getCreatedAt())
                        .build());
            }
            if (task.getStatus() == TaskStatus.DONE && task.getAssignee() != null) {
                entries.add(DashboardSummaryResponse.ActivityEntry.builder()
                        .user(task.getAssignee().getFullName())
                        .action("completed task \"" + task.getTitle() + "\"")
                        .time(task.getUpdatedAt())
                        .build());
            }
        }

        entries.sort((a, b) -> b.getTime().compareTo(a.getTime()));
        return entries.stream().limit(8).toList();
    }
}
