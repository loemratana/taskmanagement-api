package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.dto.NotificationRequest;
import com.ratana.task_management_api.dto.request.ReminderSettingsUpdateRequest;
import com.ratana.task_management_api.dto.response.NotificationResponse;
import com.ratana.task_management_api.dto.response.ReminderSettingsResponse;
import com.ratana.task_management_api.enums.NotificationPriority;
import com.ratana.task_management_api.enums.NotificationType;
import com.ratana.task_management_api.enums.ReminderLogStatus;
import com.ratana.task_management_api.exception.TaskNotFoundException;
import com.ratana.task_management_api.model.Task;
import com.ratana.task_management_api.model.TaskReminderLog;
import com.ratana.task_management_api.model.TaskReminderSchedule;
import com.ratana.task_management_api.model.TaskReminderSetting;
import com.ratana.task_management_api.repository.TaskReminderLogRepository;
import com.ratana.task_management_api.repository.TaskReminderSettingRepository;
import com.ratana.task_management_api.repository.TaskRepository;
import com.ratana.task_management_api.security.UserPrincipal;
import com.ratana.task_management_api.service.NotificationService;
import com.ratana.task_management_api.service.TaskReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskReminderServiceImpl implements TaskReminderService {

    private static final DateTimeFormatter DUE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final TaskRepository taskRepository;
    private final TaskReminderSettingRepository settingRepository;
    private final TaskReminderLogRepository logRepository;
    private final TaskAuthorizationService taskAuthorizationService;
    private final NotificationService notificationService;

    @Value("${app.reminder.default-timezone:Asia/Phnom_Penh}")
    private String defaultTimezone;

    @Value("${app.reminder.default-times:09:00,13:00,17:00}")
    private String defaultTimesCsv;

    @Override
    @Transactional(readOnly = true)
    public ReminderSettingsResponse getSettings(UUID taskId, UserPrincipal actor) {
        Task task = loadTask(taskId);
        taskAuthorizationService.assertCanView(actor, task);

        return settingRepository.findByTaskId(taskId)
                .map(this::toResponse)
                .orElseGet(() -> ReminderSettingsResponse.builder()
                        .taskId(taskId)
                        .enabled(true)
                        .timezone(defaultTimezone)
                        .reminderTimes(defaultTimes())
                        .build());
    }

    @Override
    @Transactional
    public ReminderSettingsResponse updateSettings(UUID taskId, ReminderSettingsUpdateRequest request, UserPrincipal actor) {
        Task task = loadTask(taskId);
        taskAuthorizationService.assertCanUpdate(actor, task);

        TaskReminderSetting setting = getOrCreateSettings(task);

        if (request.getEnabled() != null) {
            setting.setEnabled(request.getEnabled());
        }
        if (request.getTimezone() != null) {
            setting.setTimezone(validateTimezone(request.getTimezone()));
        }
        if (request.getReminderTimes() != null) {
            applyReminderTimes(setting, request.getReminderTimes());
        }

        setting = settingRepository.save(setting);
        return toResponse(setting);
    }

    @Override
    @Transactional
    public TaskReminderSetting getOrCreateSettings(Task task) {
        return settingRepository.findByTaskId(task.getId())
                .orElseGet(() -> createDefaultSettings(task));
    }

    private TaskReminderSetting createDefaultSettings(Task task) {
        TaskReminderSetting setting = TaskReminderSetting.builder()
                .task(task)
                .enabled(true)
                .timezone(defaultTimezone)
                .build();
        defaultTimes().forEach(time -> setting.addSchedule(
                TaskReminderSchedule.builder().reminderTime(time).active(true).build()));

        try {
            return settingRepository.saveAndFlush(setting);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with another instance provisioning the same task — use theirs.
            return settingRepository.findByTaskId(task.getId())
                    .orElseThrow(() -> e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processDueReminder(Task task, TaskReminderSchedule schedule, LocalDate reminderDate) {
        if (logRepository.existsByTaskIdAndScheduleIdAndReminderDate(task.getId(), schedule.getId(), reminderDate)) {
            return;
        }

        Task fresh = taskRepository.findById(task.getId()).orElse(null);
        boolean eligible = fresh != null
                && fresh.isActive()
                && !fresh.getStatus().isTerminal()
                && fresh.getAssignee() != null;

        TaskReminderLog reminderLog = TaskReminderLog.builder()
                .task(task)
                .schedule(schedule)
                .reminderDate(reminderDate)
                .build();

        if (!eligible) {
            reminderLog.setStatus(ReminderLogStatus.SKIPPED);
            claim(reminderLog);
            return;
        }

        reminderLog.setStatus(ReminderLogStatus.SENT);
        reminderLog.setSentAt(Instant.now());
        TaskReminderLog claimed = claim(reminderLog);
        if (claimed == null) {
            return; // another run already claimed this slot
        }

        try {
            NotificationResponse response = notificationService.send(buildRequest(fresh, schedule, reminderDate));
            claimed.setNotificationId(response.getId());
            logRepository.save(claimed);
        } catch (Exception e) {
            log.error("Failed to send reminder for task {} schedule {}: {}", fresh.getId(), schedule.getId(), e.getMessage(), e);
            claimed.setStatus(ReminderLogStatus.FAILED);
            logRepository.save(claimed);
        }
    }

    /** Returns the saved log, or null if another run already claimed this (task, schedule, date) slot. */
    private TaskReminderLog claim(TaskReminderLog reminderLog) {
        try {
            return logRepository.saveAndFlush(reminderLog);
        } catch (DataIntegrityViolationException e) {
            log.debug("Reminder slot already claimed for task {} schedule {} date {}",
                    reminderLog.getTask().getId(), reminderLog.getSchedule().getId(), reminderLog.getReminderDate());
            return null;
        }
    }

    private NotificationRequest buildRequest(Task task, TaskReminderSchedule schedule, LocalDate reminderDate) {
        UUID recipientId = task.getAssignee().getId();
        UUID senderId = resolveSenderId(task, recipientId);
        String dueDate = task.getDueDate() != null ? task.getDueDate().format(DUE_DATE_FORMAT) : "No due date set";

        return NotificationRequest.builder()
                .recipientId(recipientId)
                .senderId(senderId)
                .title("Task Reminder")
                .message("\"" + task.getTitle() + "\" is still incomplete. Due: " + dueDate
                        + " | Priority: " + task.getPriority())
                .type(NotificationType.TASK_REMINDER)
                .priority(NotificationPriority.valueOf(task.getPriority().name()))
                .referenceType("TASK")
                .referenceId(task.getId())
                .metadata(Map.of(
                        "scheduleId", schedule.getId().toString(),
                        "reminderDate", reminderDate.toString()
                ))
                .build();
    }

    private UUID resolveSenderId(Task task, UUID fallbackRecipientId) {
        if (task.getAssignedBy() != null) {
            return task.getAssignedBy().getId();
        }
        if (task.getCreator() != null) {
            return task.getCreator().getId();
        }
        return fallbackRecipientId;
    }

    private void applyReminderTimes(TaskReminderSetting setting, List<LocalTime> times) {
        setting.getSchedules().clear();
        // Flush the orphan-removal deletes now — otherwise Hibernate can insert the new rows
        // before deleting the old ones, and a kept time collides with its own stale row on
        // the (setting, reminder_time) unique constraint.
        settingRepository.saveAndFlush(setting);

        Set<LocalTime> deduped = new LinkedHashSet<>(times);
        deduped.forEach(time -> setting.addSchedule(
                TaskReminderSchedule.builder().reminderTime(time).active(true).build()));
    }

    private String validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
            return timezone;
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid timezone: " + timezone, e);
        }
    }

    private List<LocalTime> defaultTimes() {
        return Arrays.stream(defaultTimesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(LocalTime::parse)
                .toList();
    }

    private ReminderSettingsResponse toResponse(TaskReminderSetting setting) {
        List<LocalTime> times = setting.getSchedules().stream()
                .filter(TaskReminderSchedule::isActive)
                .map(TaskReminderSchedule::getReminderTime)
                .sorted(Comparator.naturalOrder())
                .toList();

        return ReminderSettingsResponse.builder()
                .taskId(setting.getTask().getId())
                .enabled(setting.isEnabled())
                .timezone(setting.getTimezone())
                .reminderTimes(times)
                .build();
    }

    private Task loadTask(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found: " + id));
    }
}
