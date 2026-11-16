package com.ratana.task_management_api.task.service;

import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.task.dto.response.ManagerAssignedKpiResponse;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.task.repository.TaskRepository;
import com.ratana.task_management_api.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The manager dashboard's numbers over the tasks a manager assigned: status counts, overdue,
 * workload per member, top performers by completion, and completions over the last week.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskKpiService {

    private static final Set<TaskStatus> COMPLETED = EnumSet.of(TaskStatus.DONE, TaskStatus.APPROVED);
    private static final Set<TaskStatus> FINISHED = EnumSet.of(TaskStatus.DONE, TaskStatus.APPROVED, TaskStatus.CANCELLED);
    private static final int TOP_PERFORMER_LIMIT = 10;

    private final TaskRepository taskRepository;

    /** Day boundaries for KPI stats (overdue, weekly completions) follow the business timezone. */
    private ZoneId reportZone = ZoneId.of("Asia/Phnom_Penh");

    @Value("${app.reminder.default-timezone:Asia/Phnom_Penh}")
    void setReportZone(String zone) {
        this.reportZone = ZoneId.of(zone);
    }

    /** KPIs over the active tasks the actor assigned (the manager dashboard). */
    @Transactional(readOnly = true)
    public ManagerAssignedKpiResponse assignedKpi(UserPrincipal actor) {
        List<Task> tasks = taskRepository.findActiveByAssignedById(actor.getId());
        LocalDate today = LocalDate.now(reportZone);

        long overdue = tasks.stream().filter(task -> isOverdue(task, today)).count();

        // Per-assignee stats; only the primary assignee is counted.
        Map<UUID, List<Task>> byAssignee = tasks.stream()
                .filter(task -> task.getAssignee() != null)
                .collect(Collectors.groupingBy(task -> task.getAssignee().getId(), LinkedHashMap::new, Collectors.toList()));

        List<ManagerAssignedKpiResponse.MemberWorkload> workload = byAssignee.values().stream()
                .map(memberTasks -> {
                    User member = memberTasks.getFirst().getAssignee();
                    return ManagerAssignedKpiResponse.MemberWorkload.builder()
                            .memberId(member.getId())
                            .memberName(member.getFullName())
                            .avatarUrl(member.getAvatarUrl())
                            .taskCount(memberTasks.stream().filter(task -> !FINISHED.contains(task.getStatus())).count())
                            .build();
                })
                .filter(member -> member.taskCount() > 0)
                .sorted(Comparator.comparingLong(ManagerAssignedKpiResponse.MemberWorkload::taskCount).reversed())
                .toList();

        List<ManagerAssignedKpiResponse.MemberPerformance> performers = byAssignee.values().stream()
                .map(memberTasks -> toPerformance(memberTasks, today))
                .sorted(Comparator.comparingDouble(ManagerAssignedKpiResponse.MemberPerformance::completionRate).reversed()
                        .thenComparing(Comparator.comparingLong(ManagerAssignedKpiResponse.MemberPerformance::totalTasks).reversed()))
                .limit(TOP_PERFORMER_LIMIT)
                .toList();

        Map<LocalDate, Long> completionsByDay = tasks.stream()
                .filter(task -> COMPLETED.contains(task.getStatus()) && task.getCompletedAt() != null)
                .collect(Collectors.groupingBy(task -> completionDate(task), Collectors.counting()));
        List<ManagerAssignedKpiResponse.DailyCompletion> weekly = new ArrayList<>();
        for (int daysAgo = 6; daysAgo >= 0; daysAgo--) {
            LocalDate day = today.minusDays(daysAgo);
            weekly.add(new ManagerAssignedKpiResponse.DailyCompletion(day, completionsByDay.getOrDefault(day, 0L)));
        }

        log.debug("Assigned-task KPI for manager {}: {} task(s), {} assignee(s)", actor.getId(), tasks.size(), byAssignee.size());
        return ManagerAssignedKpiResponse.builder()
                .totalTasks(tasks.size())
                .pendingTasks(countStatus(tasks, TaskStatus.IN_PROGRESS))
                // BLOCKED reads as to-do in the app, so it's counted here rather than vanishing from the breakdown.
                .todoTasks(countStatus(tasks, TaskStatus.TODO) + countStatus(tasks, TaskStatus.BLOCKED))
                .reviewTasks(countStatus(tasks, TaskStatus.IN_REVIEW))
                .doneTasks(tasks.stream().filter(task -> COMPLETED.contains(task.getStatus())).count())
                .rejectedTasks(countStatus(tasks, TaskStatus.REJECTED))
                .overdueTasks(overdue)
                .memberWorkload(workload)
                .weeklyCompletions(weekly)
                .topPerformers(performers)
                .build();
    }

    private ManagerAssignedKpiResponse.MemberPerformance toPerformance(List<Task> memberTasks, LocalDate today) {
        User member = memberTasks.getFirst().getAssignee();
        List<Task> completed = memberTasks.stream().filter(task -> COMPLETED.contains(task.getStatus())).toList();
        long onTime = completed.stream()
                .filter(task -> task.getDueDate() == null
                        || task.getCompletedAt() == null
                        || !completionDate(task).isAfter(task.getDueDate()))
                .count();

        return ManagerAssignedKpiResponse.MemberPerformance.builder()
                .memberId(member.getId())
                .memberName(member.getFullName())
                .avatarUrl(member.getAvatarUrl())
                .positionName(member.getPosition() != null ? member.getPosition().getName() : null)
                .totalTasks(memberTasks.size())
                .completedTasks(completed.size())
                .overdueTasks(memberTasks.stream().filter(task -> isOverdue(task, today)).count())
                .completionRate(percent(completed.size(), memberTasks.size()))
                .onTimeRate(percent(onTime, completed.size()))
                .build();
    }

    private LocalDate completionDate(Task task) {
        return task.getCompletedAt().atZone(reportZone).toLocalDate();
    }

    private static boolean isOverdue(Task task, LocalDate today) {
        return task.getDueDate() != null && task.getDueDate().isBefore(today) && !FINISHED.contains(task.getStatus());
    }

    private static long countStatus(List<Task> tasks, TaskStatus status) {
        return tasks.stream().filter(task -> task.getStatus() == status).count();
    }

    /** 0-100, one decimal place; 0 when there is nothing to divide by. */
    private static double percent(long part, long whole) {
        return whole == 0 ? 0 : Math.round(part * 1000.0 / whole) / 10.0;
    }
}
