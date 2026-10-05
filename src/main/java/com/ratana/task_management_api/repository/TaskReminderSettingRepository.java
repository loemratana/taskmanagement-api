package com.ratana.task_management_api.repository;

import com.ratana.task_management_api.model.TaskReminderSetting;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskReminderSettingRepository extends JpaRepository<TaskReminderSetting, UUID> {

    @EntityGraph(attributePaths = {"schedules"})
    Optional<TaskReminderSetting> findByTaskId(UUID taskId);

    /** Batch variant for the scheduler — one query for all candidates instead of one per task. */
    @Query("SELECT DISTINCT s FROM TaskReminderSetting s LEFT JOIN FETCH s.schedules WHERE s.task.id IN :taskIds")
    List<TaskReminderSetting> findAllByTaskIdIn(@Param("taskIds") Collection<UUID> taskIds);
}
