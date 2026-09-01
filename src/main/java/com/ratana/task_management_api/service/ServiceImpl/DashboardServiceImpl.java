package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.dto.response.DashboardSummaryResponse;
import com.ratana.task_management_api.enums.TaskStatus;
import com.ratana.task_management_api.mapper.TaskMapper;
import com.ratana.task_management_api.model.Department;
import com.ratana.task_management_api.model.Task;
import com.ratana.task_management_api.repository.DepartmentRepository;
import com.ratana.task_management_api.repository.TaskRepository;
import com.ratana.task_management_api.repository.UserRepository;
import com.ratana.task_management_api.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

        long totalEmployees = userRepository.countByRoles_NameIgnoreCase("EMPLOYEE");
        long totalManagers = userRepository.countByRoles_NameIgnoreCase("MANAGER");

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
