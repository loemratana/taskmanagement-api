package com.ratana.task_management_api.task.reminder;

import com.ratana.task_management_api.task.entity.TaskAssignment;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.task.service.TaskAuditService;
import com.ratana.task_management_api.task.service.TaskAuthorizationService;

import com.ratana.task_management_api.notification.dto.request.NotificationRequest;
import com.ratana.task_management_api.notification.dto.response.NotificationResponse;
import com.ratana.task_management_api.notification.enums.NotificationPriority;
import com.ratana.task_management_api.notification.enums.NotificationType;
import com.ratana.task_management_api.task.enums.TaskAuditAction;
import com.ratana.task_management_api.task.exception.TaskNotFoundException;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.repository.TaskRepository;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.notification.service.NotificationService;
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
import java.util.Objects;
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
    private final TaskAuditService taskAuditService;

    @Value("${app.reminder.default-timezone:Asia/Phnom_Penh}")
    private String defaultTimezone;

    @Value("${app.reminder.default-times:09:00,13:00,17:00}")
    private String defaultTimesCsv;

    @Override
    @Transactional(readOnly = true)
    public ReminderSettingsResponse getSettings(UUID taskId, UserPrincipal actor) {
        Task task = loadTask(taskId);
        taskAuthorizationService.assertCanView(actor, task);

        log.debug("Fetching reminder settings of task {} for user {}", taskId, actor.getId());
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
        ReminderSettingsResponse result = toResponse(setting);
        taskAuditService.record(task, actor.getId(), TaskAuditAction.REMINDER_SETTINGS_CHANGED,
                TaskAuditService.values("enabled", result.isEnabled(), "timezone", result.getTimezone(),
                        "reminderTimes", result.getReminderTimes().stream().map(Object::toString).toList()));
        log.info("Reminder settings of task {} updated by {}: enabled={}, timezone={}, times={}",
                taskId, actor.getId(), setting.isEnabled(), setting.getTimezone(), request.getReminderTimes());
        return result;
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
            TaskReminderSetting saved = settingRepository.saveAndFlush(setting);
            log.info("Default reminder settings created for task {} ({}, {})", task.getId(), defaultTimezone, defaultTimesCsv);
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Lost a race with another instance provisioning the same task — use theirs.
            log.debug("Reminder settings for task {} were created concurrently, reusing them", task.getId());
            return settingRepository.findByTaskId(task.getId())
                    .orElseThrow(() -> e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processDueReminder(Task task, TaskReminderSchedule schedule, LocalDate reminderDate) {
        if (logRepository.existsByTaskIdAndScheduleIdAndReminderDate(task.getId(), schedule.getId(), reminderDate)) {
            log.debug("Reminder already processed for task {} schedule {} date {}", task.getId(), schedule.getId(), reminderDate);
            return;
        }

        Task fresh = taskRepository.findById(task.getId()).orElse(null);
        boolean eligible = fresh != null
                && fresh.isActive()
                && fresh.getStatus().needsReminders()
                && fresh.getAssignee() != null;

        TaskReminderLog reminderLog = TaskReminderLog.builder()
                .task(task)
                .schedule(schedule)
                .reminderDate(reminderDate)
                .build();

        if (!eligible) {
            log.debug("Reminder skipped for task {} schedule {}: task is gone, inactive, finished or unassigned",
                    task.getId(), schedule.getId());
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
            UUID assigneeId = fresh.getAssignee().getId();
            NotificationResponse response = notificationService.send(buildRequest(fresh, schedule, reminderDate, assigneeId));
            claimed.setNotificationId(response.getId());
            logRepository.save(claimed);
            log.info("Reminder sent for task {} schedule {} to user {} (notification {})",
                    fresh.getId(), schedule.getId(), assigneeId, response.getId());
            remindCollaborators(fresh, schedule, reminderDate, assigneeId);
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

    /**
     * A shared task is everyone's work, so collaborators get the same reminder. The log row tracks the
     * primary assignee's notification; a collaborator's failed send is logged and doesn't fail the slot.
     */
    private void remindCollaborators(Task task, TaskReminderSchedule schedule, LocalDate reminderDate, UUID assigneeId) {
        if (task.getAssignments() == null) {
            return;
        }
        task.getAssignments().stream()
                .map(TaskAssignment::getUser)
                .filter(Objects::nonNull)
                .map(User::getId)
                .filter(userId -> !userId.equals(assigneeId))
                .distinct()
                .forEach(userId -> {
                    try {
                        notificationService.send(buildRequest(task, schedule, reminderDate, userId));
                        log.info("Reminder sent for task {} schedule {} to collaborator {}",
                                task.getId(), schedule.getId(), userId);
                    } catch (Exception e) {
                        log.warn("Reminder for task {} to collaborator {} failed: {}", task.getId(), userId, e.getMessage());
                    }
                });
    }

    private NotificationRequest buildRequest(Task task, TaskReminderSchedule schedule, LocalDate reminderDate,
                                             UUID recipientId) {
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
        // Diff against existing rows instead of delete-and-recreate: schedules that have fired
        // are referenced by task_reminder_logs, so removed times are deactivated, not deleted.
        // Reusing the row for a kept/re-added time also respects the (setting, reminder_time)
        // unique constraint.
        Set<LocalTime> wanted = new LinkedHashSet<>(times);
        for (TaskReminderSchedule existing : setting.getSchedules()) {
            existing.setActive(wanted.remove(existing.getReminderTime()));
        }
        wanted.forEach(time -> setting.addSchedule(
                TaskReminderSchedule.builder().reminderTime(time).active(true).build()));
    }

    private String validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
            return timezone;
        } catch (DateTimeException e) {
            log.warn("Reminder settings rejected: invalid timezone {}", timezone);
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
                .orElseThrow(() -> {
                    log.warn("Task not found: {}", id);
                    return new TaskNotFoundException("Task not found: " + id);
                });
    }
}
