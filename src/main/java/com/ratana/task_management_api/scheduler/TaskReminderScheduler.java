package com.ratana.task_management_api.scheduler;

import com.ratana.task_management_api.enums.TaskStatus;
import com.ratana.task_management_api.model.Task;
import com.ratana.task_management_api.model.TaskReminderSchedule;
import com.ratana.task_management_api.model.TaskReminderSetting;
import com.ratana.task_management_api.repository.TaskRepository;
import com.ratana.task_management_api.service.TaskReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ticks every minute, finds tasks with a due reminder slot, and hands each one to
 * {@link TaskReminderService#processDueReminder} for the actual claim-then-send work.
 * Uses a time window (not exact equality) so scheduler delay never silently skips a slot
 * within the window — see TaskReminderServiceImpl for the duplicate-prevention mechanism.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskReminderScheduler {

    private static final List<TaskStatus> TERMINAL_STATUSES = List.of(TaskStatus.DONE, TaskStatus.CANCELLED);

    private final TaskRepository taskRepository;
    private final TaskReminderService taskReminderService;
    private final Clock clock;

    @Value("${app.reminder.window-minutes:5}")
    private int windowMinutes;

    @Scheduled(fixedDelayString = "${app.reminder.check-interval-ms:60000}")
    public void checkReminders() {
        List<Task> candidates = taskRepository.findActiveNonTerminalWithAssignee(TERMINAL_STATUSES);
        if (candidates.isEmpty()) {
            return;
        }

        Map<UUID, TaskReminderSetting> settingsByTaskId = taskReminderService.findSettingsByTaskIds(
                candidates.stream().map(Task::getId).toList());

        int dueCount = 0;
        for (Task task : candidates) {
            try {
                TaskReminderSetting setting = settingsByTaskId.get(task.getId());
                if (setting == null) {
                    // First tick for this task — provision defaults (one-time cost per task).
                    setting = taskReminderService.getOrCreateSettings(task);
                }
                dueCount += processTask(task, setting);
            } catch (Exception e) {
                log.error("Reminder check failed for task {}", task.getId(), e);
            }
        }

        if (dueCount > 0) {
            log.info("Reminder scheduler processed {} due reminder(s) across {} candidate task(s)",
                    dueCount, candidates.size());
        }
    }

    private int processTask(Task task, TaskReminderSetting setting) {
        if (!setting.isEnabled()) {
            return 0;
        }

        ZoneId zone = resolveZone(setting.getTimezone());
        ZonedDateTime zonedNow = ZonedDateTime.now(clock.withZone(zone));
        LocalDate today = zonedNow.toLocalDate();
        LocalTime nowTime = zonedNow.toLocalTime();

        int due = 0;
        for (TaskReminderSchedule schedule : setting.getSchedules()) {
            if (!schedule.isActive()) {
                continue;
            }
            if (isDue(nowTime, schedule.getReminderTime())) {
                taskReminderService.processDueReminder(task, schedule, today);
                due++;
            }
        }
        return due;
    }

    private boolean isDue(LocalTime now, LocalTime reminderTime) {
        LocalTime windowEnd = reminderTime.plusMinutes(windowMinutes);
        if (windowEnd.isAfter(reminderTime)) {
            return !now.isBefore(reminderTime) && now.isBefore(windowEnd);
        }
        // Window wraps past midnight (e.g. reminder at 23:58 with a 5-minute window) — treat as open-ended today.
        return !now.isBefore(reminderTime);
    }

    private ZoneId resolveZone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            log.warn("Invalid reminder timezone '{}', falling back to UTC", timezone);
            return ZoneId.of("UTC");
        }
    }
}
