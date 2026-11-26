package com.ratana.task_management_api.task.repository;

import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.enums.TaskStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.Map;

/** Aggregate task counts, mixed into {@link TaskRepository} (implemented by TaskCountRepositoryImpl). */
public interface TaskCountRepository {

    /** Per-status count and overdue count of the tasks matching {@code spec}. */
    record StatusCount(long count, long overdue) {}

    /**
     * Tasks matching {@code spec}, grouped by status, with how many of each are overdue
     * ({@code TaskSpecifications.overdue(true)}). One query, instead of a COUNT per status.
     * Statuses with no tasks are absent.
     */
    Map<TaskStatus, StatusCount> countByStatusWithOverdue(Specification<Task> spec);
}
