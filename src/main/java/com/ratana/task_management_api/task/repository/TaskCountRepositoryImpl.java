package com.ratana.task_management_api.task.repository;

import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.task.specification.TaskSpecifications;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.EnumMap;
import java.util.Map;

/** Spring Data picks this up by name as the implementation of {@link TaskCountRepository}. */
class TaskCountRepositoryImpl implements TaskCountRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Map<TaskStatus, StatusCount> countByStatusWithOverdue(Specification<Task> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<Task> root = query.from(Task.class);

        // SELECT status, COUNT(*), SUM(CASE WHEN <overdue> THEN 1 ELSE 0 END) ... GROUP BY status.
        // The overdue rule is the same specification the task list filter uses.
        Path<TaskStatus> status = root.get("status");
        Predicate overdue = TaskSpecifications.overdue(true).toPredicate(root, query, cb);
        Expression<Long> overdueFlag = cb.<Long>selectCase().when(overdue, 1L).otherwise(0L);

        Predicate where = spec.toPredicate(root, query, cb);
        if (where != null) {
            query.where(where);
        }
        query.multiselect(status, cb.count(root), cb.sum(overdueFlag)).groupBy(status);

        Map<TaskStatus, StatusCount> counts = new EnumMap<>(TaskStatus.class);
        for (Object[] row : entityManager.createQuery(query).getResultList()) {
            if (row[0] != null) {
                long overdueCount = row[2] == null ? 0L : ((Number) row[2]).longValue();
                counts.put((TaskStatus) row[0], new StatusCount(((Number) row[1]).longValue(), overdueCount));
            }
        }
        return counts;
    }
}
