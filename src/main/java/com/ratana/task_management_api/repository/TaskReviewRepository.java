package com.ratana.task_management_api.repository;

import com.ratana.task_management_api.model.TaskReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskReviewRepository extends JpaRepository<TaskReview, UUID> {

    Optional<TaskReview> findByTaskId(UUID taskId);

    /** Batch lookup for a page of tasks — avoids one query per row when mapping a list. */
    List<TaskReview> findByTaskIdIn(Collection<UUID> taskIds);
}
