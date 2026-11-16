package com.ratana.task_management_api.task.repository;

import com.ratana.task_management_api.task.entity.TaskLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskLogRepository extends JpaRepository<TaskLog, UUID> {

    /** A task's history, oldest first, with the acting user loaded for display. */
    @EntityGraph(attributePaths = {"user"})
    List<TaskLog> findByTaskIdOrderByCreatedAtAsc(UUID taskId);
}
