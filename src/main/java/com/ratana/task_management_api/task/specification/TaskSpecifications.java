package com.ratana.task_management_api.task.specification;

import java.time.LocalDate;
import com.ratana.task_management_api.task.dto.request.TaskFilterRequest;
import com.ratana.task_management_api.task.enums.Priority;
import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.task.enums.ViewScope;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.entity.TaskAssignment;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Builds JPA specs so list queries enforce view scope in the DB (not in memory).
 */
public final class TaskSpecifications {

    private TaskSpecifications() {}

    public static Specification<Task> visibleTo(UserPrincipal actor, ViewScope scope,
                                                Collection<UUID> teamMemberIds) {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("department", JoinType.LEFT);
                root.fetch("assignee", JoinType.LEFT);
                root.fetch("creator", JoinType.LEFT);
                query.distinct(true);
            }

            UUID actorId = actor.getId();

            return switch (scope) {
                case ALL -> cb.conjunction();
                case DEPARTMENT -> {
                    if (actor.getDepartmentId() == null) {
                        yield cb.disjunction(); // no department → see nothing via this scope
                    }
                    yield cb.equal(root.get("department").get("id"), actor.getDepartmentId());
                }
                case TEAM -> {
                    List<Predicate> teamPredicates = new ArrayList<>();
                    teamPredicates.add(cb.equal(root.get("assignee").get("id"), actorId));
                    teamPredicates.add(cb.equal(root.get("creator").get("id"), actorId));
                    if (teamMemberIds != null && !teamMemberIds.isEmpty()) {
                        teamPredicates.add(root.get("assignee").get("id").in(teamMemberIds));
                    }
                    if (query != null) {
                        teamPredicates.add(isCollaborator(root, query, cb, actorId));
                    }
                    yield cb.or(teamPredicates.toArray(Predicate[]::new));
                }
                case OWN -> {
                    List<Predicate> ownPredicates = new ArrayList<>();
                    ownPredicates.add(cb.equal(root.get("assignee").get("id"), actorId));
                    ownPredicates.add(cb.equal(root.get("creator").get("id"), actorId));
                    if (query != null) {
                        ownPredicates.add(isCollaborator(root, query, cb, actorId));
                    }
                    yield cb.or(ownPredicates.toArray(Predicate[]::new));
                }
            };
        };
    }

    /**
     * Actor is an additional assignee (task_assignments). Matches Task.isAssignee, so a collaborator
     * who can open a task by id also finds it in the list (DEF-13).
     */
    private static Predicate isCollaborator(Root<Task> root, CriteriaQuery<?> query, CriteriaBuilder cb, UUID actorId) {
        Subquery<UUID> collaboration = query.subquery(UUID.class);
        Root<TaskAssignment> assignment = collaboration.from(TaskAssignment.class);
        collaboration.select(assignment.get("id"))
                .where(cb.equal(assignment.get("task"), root),
                        cb.equal(assignment.get("user").get("id"), actorId));
        return cb.exists(collaboration);
    }

    public static Specification<Task> withStatus(TaskStatus status) {
        return (root, query, cb) -> status == null
                ? cb.conjunction()
                : cb.equal(root.get("status"), status);
    }

    public static Specification<Task> withKeyword(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return cb.conjunction();
            }
            String like = "%" + keyword.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("description")), like)
            );
        };
    }

    public static Specification<Task> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    /** Excludes group-task parents, whose work is counted through their member subtasks. */
    public static Specification<Task> notGroupTask() {
        return (root, query, cb) -> cb.isFalse(root.get("groupTask"));
    }

    public static Specification<Task> withPriority(Priority priority) {
        return (root, query, cb) -> priority == null
                ? cb.conjunction()
                : cb.equal(root.get("priority"), priority);
    }

    public static Specification<Task> withDepartment(UUID departmentId) {
        return (root, query, cb) -> departmentId == null
                ? cb.conjunction()
                : cb.equal(root.get("department").get("id"), departmentId);
    }

    /** Primary assignee or collaborator (task_assignments) — same rule as Task.isAssignee. */
    public static Specification<Task> withAssignee(UUID assigneeId) {
        return (root, query, cb) -> {
            if (assigneeId == null) {
                return cb.conjunction();
            }
            Predicate primary = cb.equal(root.get("assignee").get("id"), assigneeId);
            return query == null ? primary : cb.or(primary, isCollaborator(root, query, cb, assigneeId));
        };
    }

    public static Specification<Task> withCreator(UUID creatorId) {
        return (root, query, cb) -> creatorId == null
                ? cb.conjunction()
                : cb.equal(root.get("creator").get("id"), creatorId);
    }

    public static Specification<Task> withAssignedBy(UUID assignerId) {
        return (root, query, cb) -> assignerId == null
                ? cb.conjunction()
                : cb.equal(root.get("assignedBy").get("id"), assignerId);
    }

    /**
     * With an assignee: drops subtasks whose parent is also that person's task, because the app shows
     * those inside the parent's card. Everything else stays, including a group-task member's own
     * subtask (its group parent has no assignee), so counts match the rows of the "My tasks" list.
     */
    public static Specification<Task> excludeNestedSubtasks(Boolean enabled, UUID assigneeId) {
        return (root, query, cb) -> {
            if (!Boolean.TRUE.equals(enabled) || assigneeId == null || query == null) {
                return cb.conjunction();
            }
            Subquery<UUID> ownParent = query.subquery(UUID.class);
            Root<Task> parent = ownParent.from(Task.class);

            Subquery<UUID> collaboration = query.subquery(UUID.class);
            Root<TaskAssignment> assignment = collaboration.from(TaskAssignment.class);
            collaboration.select(assignment.get("id"))
                    .where(cb.equal(assignment.get("task"), parent),
                            cb.equal(assignment.get("user").get("id"), assigneeId));

            ownParent.select(parent.get("id"))
                    .where(cb.equal(parent, root.get("parentTask")),
                            cb.or(cb.equal(parent.join("assignee", JoinType.LEFT).get("id"), assigneeId),
                                    cb.exists(collaboration)));
            return cb.not(cb.exists(ownParent));
        };
    }

    public static Specification<Task> topLevelOnly(Boolean enabled) {
        return (root, query, cb) -> Boolean.TRUE.equals(enabled)
                ? cb.isNull(root.get("parentTask"))
                : cb.conjunction();
    }

    private static final List<TaskStatus> FINISHED_STATUSES =
            List.of(TaskStatus.DONE, TaskStatus.APPROVED, TaskStatus.CANCELLED);

    /** "Today" for overdue checks: the business timezone, not the server's (set once at startup). */
    private static volatile java.time.ZoneId businessZone = java.time.ZoneId.of("Asia/Phnom_Penh");

    public static void setBusinessZone(java.time.ZoneId zone) {
        businessZone = zone;
    }

    public static Specification<Task> overdue(Boolean enabled) {
        return (root, query, cb) -> {
            if (!Boolean.TRUE.equals(enabled)) {
                return cb.conjunction();
            }
            return cb.and(
                    cb.isNotNull(root.get("dueDate")),
                    cb.lessThan(root.get("dueDate"), LocalDate.now(businessZone)),
                    cb.not(root.get("status").in(FINISHED_STATUSES)));
        };
    }

    /** Combines every optional filter field on {@link TaskFilterRequest} with AND. */
    public static Specification<Task> fromFilter(TaskFilterRequest filter) {
        return withStatus(filter.getTaskStatus())
                .and(withPriority(filter.getPriority()))
                .and(withDepartment(filter.getDepartmentId()))
                .and(withAssignee(filter.getAssigneeId()))
                .and(withCreator(filter.getCreatedById()))
                .and(withAssignedBy(filter.getAssignedById()))
                .and(topLevelOnly(filter.getTopLevelOnly()))
                .and(excludeNestedSubtasks(filter.getExcludeNestedSubtasks(), filter.getAssigneeId()))
                .and(overdue(filter.getOverdue()))
                .and(withKeyword(filter.getTitleContains()));
    }
}
