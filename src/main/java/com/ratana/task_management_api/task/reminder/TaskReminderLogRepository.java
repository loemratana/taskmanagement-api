package com.ratana.task_management_api.task.reminder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface TaskReminderLogRepository extends JpaRepository<TaskReminderLog, UUID> {

    boolean existsByTaskIdAndScheduleIdAndReminderDate(UUID taskId, UUID scheduleId, LocalDate reminderDate);
}
