package com.ratana.task_management_api.dashboard.service;

import com.ratana.task_management_api.dashboard.dto.request.DashboardFilter;
import com.ratana.task_management_api.dashboard.dto.response.DashboardSummaryResponse;
import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.task.mapper.TaskMapper;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.organization.repository.DepartmentRepository;
import com.ratana.task_management_api.task.repository.TaskRepository;
import com.ratana.task_management_api.user.repository.UserRepository;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.task.specification.TaskSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ratana.task_management_api.dashboard.specification.DashboardSpecification.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final String ROLE_ADMIN    = "ADMIN";
    private static final String ROLE_MANAGER  = "MANAGER";
    private static final String ROLE_EMPLOYEE = "EMPLOYEE";

    private static final int RECENT_LIMIT   = 10;
    private static final int ACTIVITY_LIMIT = 10;

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
    private final Clock clock;

    /** "Today" for overdue counts is the business date, not the server's (UTC in Docker). */
    @Value("${app.reminder.default-timezone:Asia/Phnom_Penh}")
    private ZoneId businessZone = ZoneId.of("Asia/Phnom_Penh");

    // =====================================================================
    // Public API
    // =====================================================================

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(UserPrincipal principal, DashboardFilter filter) {
        if (principal == null) {
            throw new AccessDeniedException("Unauthenticated");
        }

        User currentUser = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isAdmin = hasRoleName(currentUser, ROLE_ADMIN);
        // Archived tasks never count, matching /tasks/me/assigned-kpi. A group task counts once per
        // member (its subtasks), not again as the parent.
        Specification<Task> spec = resolveScope(currentUser, isAdmin, filter)
                .and(TaskSpecifications.activeOnly())
                .and(TaskSpecifications.notGroupTask());

        log.debug("Building dashboard summary for user={} (admin={}) with filter={}",
                currentUser.getId(), isAdmin, filter);

        return buildSummary(spec, isAdmin);
    }

    // =====================================================================
    // Core builder
    // =====================================================================

    private DashboardSummaryResponse buildSummary(Specification<Task> spec, boolean isAdmin) {
        long totalTasks      = taskRepository.count(spec);
        long activeTasks     = taskRepository.count(spec.and(hasStatus(TaskStatus.IN_PROGRESS)));
        long completedTasks  = taskRepository.count(spec.and(hasStatus(TaskStatus.DONE)));
        long pendingApproval = taskRepository.count(spec.and(hasStatus(TaskStatus.IN_REVIEW)));
        long overdueTasks    = taskRepository.count(spec.and(overdue(LocalDate.ofInstant(clock.instant(), businessZone), FINISHED_STATUSES)));

        Long totalEmployees = isAdmin ? userRepository.countByRoles_NameIgnoreCase(ROLE_EMPLOYEE) : null;
        Long totalManagers  = isAdmin ? userRepository.countByRoles_NameIgnoreCase(ROLE_MANAGER)  : null;

        int fetchLimit = Math.max(RECENT_LIMIT, ACTIVITY_LIMIT) * 2;
        List<Task> recent = taskRepository
                .findAll(spec, PageRequest.of(0, fetchLimit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();

        log.debug("Dashboard counts (admin={}): total={}, active={}, completed={}, pendingApproval={}, overdue={}",
                isAdmin, totalTasks, activeTasks, completedTasks, pendingApproval, overdueTasks);

        return DashboardSummaryResponse.builder()
                .totalEmployees(totalEmployees)
                .totalManagers(totalManagers)
                .totalTasks(totalTasks)
                .activeTasks(activeTasks)
                .completedTasks(completedTasks)
                .pendingApproval(pendingApproval)
                .overdueTasks(overdueTasks)
                .taskStatusBreakdown(buildStatusBreakdown(spec))
                .departmentPerformance(buildDepartmentPerformance(spec, isAdmin))
                .recentTasks(taskMapper.toListResponse(recent.stream().limit(RECENT_LIMIT).toList()))
                .recentActivities(buildRecentActivities(recent))
                .build();
    }

    // =====================================================================
    // Scoped aggregations
    // =====================================================================

    /** Scoped status breakdown: counts tasks within the current spec, grouped by status. */
    private List<DashboardSummaryResponse.StatusCount> buildStatusBreakdown(Specification<Task> spec) {
        List<DashboardSummaryResponse.StatusCount> breakdown = new ArrayList<>();
        for (TaskStatus status : STATUS_BREAKDOWN_ORDER) {
            breakdown.add(DashboardSummaryResponse.StatusCount.builder()
                    .status(status.name())
                    .count(taskRepository.count(spec.and(hasStatus(status))))
                    .build());
        }
        return breakdown;
    }

    /**
     * Scoped department performance.
     * - ADMIN sees every active department (even zero-count ones) so the chart is stable.
     * - Non-admins only see departments that actually appear in their scoped tasks.
     */
    private List<DashboardSummaryResponse.DepartmentTaskCount> buildDepartmentPerformance(
            Specification<Task> spec, boolean isAdmin) {

        Map<UUID, Long> counts = new LinkedHashMap<>();
        for (Task task : taskRepository.findAll(spec)) {
            if (task.getDepartment() == null) {
                continue;
            }
            counts.merge(task.getDepartment().getId(), 1L, Long::sum);
        }

        List<DashboardSummaryResponse.DepartmentTaskCount> performance = new ArrayList<>();
        for (Department dept : departmentRepository.findAllByOrderByNameAsc()) {
            if (!dept.isActive()) {
                continue;
            }
            Long count = counts.get(dept.getId());
            if (!isAdmin && count == null) {
                continue; // hide departments outside the user's scope
            }
            performance.add(DashboardSummaryResponse.DepartmentTaskCount.builder()
                    .department(dept.getName())
                    .count(count == null ? 0L : count)
                    .build());
        }
        return performance;
    }

    // =====================================================================
    // Scope resolution
    // =====================================================================

    private Specification<Task> resolveScope(User user, boolean isAdmin, DashboardFilter filter) {
        DashboardFilter f = (filter != null) ? filter : new DashboardFilter();

        if (isAdmin) {
            return allOf(
                    assignedBy(f.getManagerId()),
                    byEmployee(f.getEmployeeId()),
                    hasDepartment(f.getDepartmentId()),
                    createdBetween(f.getStartDate(), f.getEndDate())
            );
        }

        // Non-admins cannot use admin-only filters — fail loudly, don't silently ignore.
        if (f.getManagerId() != null || f.getDepartmentId() != null || f.getEmployeeId() != null) {
            throw new AccessDeniedException("Only ADMIN can filter by managerId/departmentId/employeeId");
        }

        if (hasRoleName(user, ROLE_MANAGER)) {
            return allOf(
                    assignedBy(user.getId()),
                    createdBetween(f.getStartDate(), f.getEndDate())
            );
        }

        if (hasRoleName(user, ROLE_EMPLOYEE)) {
            return allOf(
                    byEmployee(user.getId()),
                    createdBetween(f.getStartDate(), f.getEndDate())
            );
        }

        log.warn("User {} has no recognised active role; returning empty dashboard scope", user.getId());
        return none();
    }

    // =====================================================================
    // Recent activity feed
    // =====================================================================

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
        return entries.stream().limit(ACTIVITY_LIMIT).toList();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static boolean hasRoleName(User user, String role) {
        if (user == null || user.getRoles() == null || role == null) return false;
        return user.getRoles().stream()
                .anyMatch(r -> r != null
                        && r.isActive()
                        && r.getName() != null
                        && role.equalsIgnoreCase(r.getName()));
    }
}
