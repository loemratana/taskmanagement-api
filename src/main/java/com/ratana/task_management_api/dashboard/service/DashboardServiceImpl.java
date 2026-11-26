package com.ratana.task_management_api.dashboard.service;

import com.ratana.task_management_api.dashboard.dto.request.DashboardFilter;
import com.ratana.task_management_api.dashboard.dto.response.DashboardSummaryResponse;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.organization.dto.response.DepartmentResponse;
import com.ratana.task_management_api.organization.service.DepartmentService;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.task.mapper.TaskMapper;
import com.ratana.task_management_api.task.specification.TaskSpecifications;
import com.ratana.task_management_api.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ratana.task_management_api.dashboard.specification.DashboardSpecification.*;

/**
 * Dashboard summary in a handful of queries, since each one is a network round trip:
 * <ol>
 *   <li>one GROUP BY department, status: every count, the status breakdown and the department chart;</li>
 *   <li>one GROUP BY role (admin only): employee and manager totals;</li>
 *   <li>the 20 newest tasks with their people joined in (no count query), plus the mapper's
 *       batched loads of assignments/subtasks.</li>
 * </ol>
 * The caller's roles come from the request principal and the department list from the cache.
 */
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

    private final UserRepository userRepository;
    private final DepartmentService departmentService;
    private final TaskMapper taskMapper;
    private final Clock clock;
    private final EntityManager entityManager;

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

        // Roles from the authenticated principal (active roles only), not another user query.
        boolean isAdmin = hasRole(principal, ROLE_ADMIN);
        // Archived tasks never count, matching /tasks/me/assigned-kpi. A group task counts once per
        // member (its subtasks), not again as the parent.
        Specification<Task> spec = resolveScope(principal, isAdmin, filter)
                .and(TaskSpecifications.activeOnly())
                .and(TaskSpecifications.notGroupTask());

        log.debug("Building dashboard summary for user={} (admin={}) with filter={}",
                principal.getId(), isAdmin, filter);

        return buildSummary(spec, isAdmin);
    }

    // =====================================================================
    // Core builder
    // =====================================================================

    private DashboardSummaryResponse buildSummary(Specification<Task> spec, boolean isAdmin) {
        TaskCounts counts = countTasks(spec, LocalDate.ofInstant(clock.instant(), businessZone));

        Long totalEmployees = null;
        Long totalManagers = null;
        if (isAdmin) {
            Map<String, Long> byRole = countUsersByRole();
            totalEmployees = byRole.getOrDefault(ROLE_EMPLOYEE, 0L);
            totalManagers = byRole.getOrDefault(ROLE_MANAGER, 0L);
        }

        int fetchLimit = Math.max(RECENT_LIMIT, ACTIVITY_LIMIT) * 2;
        List<Task> recent = findNewest(spec, fetchLimit);

        log.debug("Dashboard counts (admin={}): total={}, byStatus={}, overdue={}",
                isAdmin, counts.total, counts.byStatus, counts.overdue);

        return DashboardSummaryResponse.builder()
                .totalEmployees(totalEmployees)
                .totalManagers(totalManagers)
                .totalTasks(counts.total)
                .activeTasks(counts.byStatus.getOrDefault(TaskStatus.IN_PROGRESS, 0L))
                .completedTasks(counts.byStatus.getOrDefault(TaskStatus.DONE, 0L))
                .pendingApproval(counts.byStatus.getOrDefault(TaskStatus.IN_REVIEW, 0L))
                .overdueTasks(counts.overdue)
                .taskStatusBreakdown(buildStatusBreakdown(counts.byStatus))
                .departmentPerformance(buildDepartmentPerformance(counts.byDepartment, isAdmin))
                .recentTasks(taskMapper.toListResponse(recent.stream().limit(RECENT_LIMIT).toList()))
                .recentActivities(buildRecentActivities(recent))
                .build();
    }

    // =====================================================================
    // Scoped aggregations
    // =====================================================================

    /** Every task count the dashboard shows, from one GROUP BY query. */
    private static final class TaskCounts {
        long total;
        long overdue;
        final Map<TaskStatus, Long> byStatus = new EnumMap<>(TaskStatus.class);
        final Map<UUID, Long> byDepartment = new HashMap<>();
    }

    /**
     * SELECT department_id, status, COUNT(*), SUM(due_date &lt; today) FROM tasks WHERE spec
     * GROUP BY department_id, status. Overdue = due before today and not finished
     * (same rule as DashboardSpecification#overdue).
     */
    private TaskCounts countTasks(Specification<Task> spec, LocalDate today) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<Task> root = query.from(Task.class);

        Path<UUID> department = root.get("department").get("id");
        Path<TaskStatus> status = root.get("status");
        Expression<Long> dueBeforeToday = cb.<Long>selectCase()
                .when(cb.lessThan(root.get("dueDate"), today), 1L)
                .otherwise(0L);

        Predicate where = spec.toPredicate(root, query, cb);
        if (where != null) {
            query.where(where);
        }
        query.multiselect(department, status, cb.count(root), cb.sum(dueBeforeToday))
                .groupBy(department, status);

        TaskCounts counts = new TaskCounts();
        for (Object[] row : entityManager.createQuery(query).getResultList()) {
            UUID departmentId = (UUID) row[0];
            TaskStatus rowStatus = (TaskStatus) row[1];
            long count = ((Number) row[2]).longValue();
            long dueBefore = row[3] == null ? 0L : ((Number) row[3]).longValue();

            counts.total += count;
            if (rowStatus != null) {
                counts.byStatus.merge(rowStatus, count, Long::sum);
                if (!FINISHED_STATUSES.contains(rowStatus)) {
                    counts.overdue += dueBefore;
                }
            }
            if (departmentId != null) {
                counts.byDepartment.merge(departmentId, count, Long::sum);
            }
        }
        return counts;
    }

    /** Users holding each role, keyed by upper-case role name. */
    private Map<String, Long> countUsersByRole() {
        Map<String, Long> byRole = new HashMap<>();
        for (Object[] row : userRepository.countUsersByRoleName(List.of(ROLE_EMPLOYEE, ROLE_MANAGER))) {
            byRole.put((String) row[0], ((Number) row[1]).longValue());
        }
        return byRole;
    }

    /**
     * The newest tasks in scope with their department and people joined in: one query, and no
     * COUNT query (a Page would add one that nothing reads).
     */
    private List<Task> findNewest(Specification<Task> spec, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Task> query = cb.createQuery(Task.class);
        Root<Task> root = query.from(Task.class);
        // To-one joins only, so LIMIT still applies in SQL (a collection fetch would page in memory).
        root.fetch("department", JoinType.LEFT);
        root.fetch("assignee", JoinType.LEFT);
        root.fetch("creator", JoinType.LEFT);
        root.fetch("assignedBy", JoinType.LEFT);

        Predicate where = spec.toPredicate(root, query, cb);
        if (where != null) {
            query.where(where);
        }
        query.select(root).orderBy(cb.desc(root.get("createdAt")));
        return entityManager.createQuery(query).setMaxResults(limit).getResultList();
    }

    /** Scoped status breakdown, in a fixed order. */
    private List<DashboardSummaryResponse.StatusCount> buildStatusBreakdown(Map<TaskStatus, Long> counts) {
        List<DashboardSummaryResponse.StatusCount> breakdown = new ArrayList<>();
        for (TaskStatus status : STATUS_BREAKDOWN_ORDER) {
            breakdown.add(DashboardSummaryResponse.StatusCount.builder()
                    .status(status.name())
                    .count(counts.getOrDefault(status, 0L))
                    .build());
        }
        return breakdown;
    }

    /**
     * Scoped department performance.
     * - ADMIN sees every active department (even zero-count ones) so the chart is stable.
     * - Non-admins only see departments that actually appear in their scoped tasks.
     * Departments come from the cached list (GET /departments), sorted by name.
     */
    private List<DashboardSummaryResponse.DepartmentTaskCount> buildDepartmentPerformance(
            Map<UUID, Long> counts, boolean isAdmin) {

        List<DashboardSummaryResponse.DepartmentTaskCount> performance = new ArrayList<>();
        for (DepartmentResponse dept : departmentService.getAllFlat()) {
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

    private Specification<Task> resolveScope(UserPrincipal user, boolean isAdmin, DashboardFilter filter) {
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

        if (hasRole(user, ROLE_MANAGER)) {
            return allOf(
                    assignedBy(user.getId()),
                    createdBetween(f.getStartDate(), f.getEndDate())
            );
        }

        if (hasRole(user, ROLE_EMPLOYEE)) {
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

    /** Principal roles hold only active roles (UserPrincipal#createUserPrincipal filters them). */
    private static boolean hasRole(UserPrincipal principal, String role) {
        return principal.getRoles() != null
                && principal.getRoles().stream().anyMatch(role::equalsIgnoreCase);
    }
}
