package com.ratana.task_management_api.utility;

import com.ratana.task_management_api.dto.request.TaskFilterRequest;
import com.ratana.task_management_api.enums.Priority;
import com.ratana.task_management_api.enums.TaskStatus;
import com.ratana.task_management_api.enums.ViewScope;
import com.ratana.task_management_api.model.Task;
import com.ratana.task_management_api.security.UserPrincipal;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
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
                    yield cb.or(teamPredicates.toArray(Predicate[]::new));
                }
                case OWN -> cb.or(
                        cb.equal(root.get("assignee").get("id"), actorId),
                        cb.equal(root.get("creator").get("id"), actorId)
                );
            };
        };
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

    public static Specification<Task> withAssignee(UUID assigneeId) {
        return (root, query, cb) -> assigneeId == null
                ? cb.conjunction()
                : cb.equal(root.get("assignee").get("id"), assigneeId);
    }

    public static Specification<Task> withCreator(UUID creatorId) {
        return (root, query, cb) -> creatorId == null
                ? cb.conjunction()
                : cb.equal(root.get("creator").get("id"), creatorId);
    }

    public static Specification<Task> withAssignedBy(UUID assignedById) {
        return (root, query, cb) -> assignedById == null
                ? cb.conjunction()
                : cb.equal(root.get("assignedBy").get("id"), assignedById);
    }

    /** Combines every optional filter field on {@link TaskFilterRequest} with AND. */
    public static Specification<Task> fromFilter(TaskFilterRequest filter) {
        return withStatus(filter.getTaskStatus())
                .and(withPriority(filter.getPriority()))
                .and(withDepartment(filter.getDepartmentId()))
                .and(withAssignee(filter.getAssigneeId()))
                .and(withCreator(filter.getCreatedById()))
                .and(withAssignedBy(filter.getAssignedById()))
                .and(withKeyword(filter.getTitleContains()));
    }
}
