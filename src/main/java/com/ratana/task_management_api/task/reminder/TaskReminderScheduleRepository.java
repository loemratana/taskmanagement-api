package com.ratana.task_management_api.task.reminder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TaskReminderScheduleRepository extends JpaRepository<TaskReminderSchedule, UUID> {
}
