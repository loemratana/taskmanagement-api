package com.ratana.task_management_api.repository;

import com.ratana.task_management_api.model.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {

    List<TaskComment> findByTaskIdOrderByCreatedAtAsc(UUID taskId);

    /** Batched comment counts for a page of tasks — avoids one COUNT query per task row. */
    @Query("SELECT c.task.id, COUNT(c) FROM TaskComment c WHERE c.task.id IN :taskIds GROUP BY c.task.id")
    List<Object[]> countGroupedByTaskIdIn(@Param("taskIds") List<UUID> taskIds);
}
