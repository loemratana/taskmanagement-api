package com.ratana.task_management_api.repository;

import com.ratana.task_management_api.model.TaskReminderSetting;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskReminderSettingRepository extends JpaRepository<TaskReminderSetting, UUID> {

    @EntityGraph(attributePaths = {"schedules"})
    Optional<TaskReminderSetting> findByTaskId(UUID taskId);
}
