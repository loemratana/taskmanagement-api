package com.ratana.task_management_api.dashboard.specification;

import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.task.entity.Task;
import jakarta.persistence.criteria.Path;
import org.springframework.data.jpa.domain.Specification;


import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;

public final  class DashboardSpecification {

    private DashboardSpecification() {}

    /**
     * Tasks assigned by a given user (Task.assignedBy, set on create-with-assignee and on reassign).
     * Matches GET /tasks/me/assigned-kpi. No-op when id is null.
     */
    public static Specification<Task> assignedBy(UUID assignerId) {
        return (root, query, cb) -> assignerId == null
                ? cb.conjunction()
                : cb.equal(root.get("assignedBy").get("id"), assignerId);
    }

    /** Tasks assigned to a given employee. No-op when id is null. */
    public static Specification<Task> byEmployee(UUID employeeId) {
        return (root, query, cb) -> employeeId == null
                ? cb.conjunction()
                : cb.equal(root.get("assignee").get("id"), employeeId);
    }

    /** Tasks in a given department. No-op when id is null. */
    public static Specification<Task> hasDepartment(UUID departmentId) {
        return (root, query, cb) -> departmentId == null
                ? cb.conjunction()
                : cb.equal(root.get("department").get("id"), departmentId);
    }

    /** Tasks created within [startDate 00:00, endDate 23:59:59.999999999]. */
    public static Specification<Task> createdBetween(LocalDate startDate, LocalDate endDate) {
        return (root, query, cb) -> {
            if (startDate == null && endDate == null) {
                return cb.conjunction();
            }
            // NOTE: assumes Task.createdAt is java.time.Instant (see note below).
            Path<Instant> createdAt = root.get("createdAt");

            if (startDate != null && endDate != null) {
                if (endDate.isBefore(startDate)) {
                    return cb.disjunction(); // invalid range -> no results
                }
                return cb.between(
                        createdAt,
                        startDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
                        endDate.plusDays(1).atStartOfDay().minusNanos(1)
                                .toInstant(java.time.ZoneOffset.UTC)
                );
            }
            if (startDate != null) {
                return cb.greaterThanOrEqualTo(
                        createdAt,
                        startDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
            }
            return cb.lessThan(
                    createdAt,
                    endDate.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        };
    }

    /** Tasks matching a status. */
    public static Specification<Task> hasStatus(TaskStatus status) {
        return (root, query, cb) -> status == null
                ? cb.conjunction()
                : cb.equal(root.get("status"), status);
    }

    /** Tasks whose status is in the given set. */
    public static Specification<Task> statusIn(Collection<TaskStatus> statuses) {
        return (root, query, cb) -> (statuses == null || statuses.isEmpty())
                ? cb.conjunction()
                : root.get("status").in(statuses);
    }

    /** Overdue: has a due date before {@code today} and is not in a finished status. */
    public static Specification<Task> overdue(LocalDate today, Collection<TaskStatus> finishedStatuses) {
        return (root, query, cb) -> cb.and(
                cb.isNotNull(root.get("dueDate")),
                cb.lessThan(root.get("dueDate"), today),
                cb.not(root.get("status").in(finishedStatuses))
        );
    }

    /** Explicit empty spec — used for fail-closed scope. */
    public static Specification<Task> none() {
        return (root, query, cb) -> cb.disjunction();
    }

    /** AND-combines specs, skipping nulls. Safe with zero args (returns true). */
    @SafeVarargs
    public static Specification<Task> allOf(Specification<Task>... specs) {
        Specification<Task> result = (root, query, cb) -> cb.conjunction();
        for (Specification<Task> s : specs) {
            if (s != null) {
                result = result.and(s);
            }
        }
        return result;
    }
}
