package com.ratana.task_management_api.scheduler;

import com.ratana.task_management_api.enums.TaskStatus;
import com.ratana.task_management_api.model.Task;
import com.ratana.task_management_api.model.TaskReminderSchedule;
import com.ratana.task_management_api.model.TaskReminderSetting;
import com.ratana.task_management_api.repository.TaskRepository;
import com.ratana.task_management_api.service.TaskReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskReminderSchedulerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

    @Mock private TaskRepository taskRepository;
    @Mock private TaskReminderService taskReminderService;

    private TaskReminderScheduler scheduler;

    /** Fixed "now" = 09:02 Asia/Phnom_Penh — inside the default 5-minute window for a 09:00 schedule. */
    private final Clock clock = Clock.fixed(
            ZonedDateTime.of(2026, 8, 31, 9, 2, 0, 0, ZONE).toInstant(), ZONE);

    @BeforeEach
    void setUp() {
        scheduler = new TaskReminderScheduler(taskRepository, taskReminderService, clock);
        ReflectionTestUtils.setField(scheduler, "windowMinutes", 5);
    }

    private Task newTask() {
        Task task = new Task();
        task.setId(UUID.randomUUID());
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setActive(true);
        return task;
    }

    private TaskReminderSetting settingWith(Task task, boolean enabled, LocalTime... times) {
        TaskReminderSetting setting = TaskReminderSetting.builder()
                .task(task).enabled(enabled).timezone(ZONE.getId()).build();
        for (LocalTime t : times) {
            setting.addSchedule(TaskReminderSchedule.builder().reminderTime(t).active(true).build());
        }
        return setting;
    }

    @Test
    @DisplayName("A schedule within the window is due and gets processed")
    void dueSchedule_isProcessed() {
        Task task = newTask();
        when(taskRepository.findActiveNonTerminalWithAssignee(any())).thenReturn(List.of(task));
        when(taskReminderService.getOrCreateSettings(task))
                .thenReturn(settingWith(task, true, LocalTime.of(9, 0)));

        scheduler.checkReminders();

        verify(taskReminderService).processDueReminder(eq(task), any(TaskReminderSchedule.class), eq(LocalDate.of(2026, 8, 31)));
    }

    @Test
    @DisplayName("A schedule outside the window is not due")
    void notYetDueSchedule_isSkipped() {
        Task task = newTask();
        when(taskRepository.findActiveNonTerminalWithAssignee(any())).thenReturn(List.of(task));
        when(taskReminderService.getOrCreateSettings(task))
                .thenReturn(settingWith(task, true, LocalTime.of(13, 0)));

        scheduler.checkReminders();

        verify(taskReminderService, never()).processDueReminder(any(), any(), any());
    }

    @Test
    @DisplayName("Disabled setting short-circuits before any schedule is evaluated")
    void disabledSetting_skipsEntirely() {
        Task task = newTask();
        when(taskRepository.findActiveNonTerminalWithAssignee(any())).thenReturn(List.of(task));
        when(taskReminderService.getOrCreateSettings(task))
                .thenReturn(settingWith(task, false, LocalTime.of(9, 0)));

        scheduler.checkReminders();

        verify(taskReminderService, never()).processDueReminder(any(), any(), any());
    }

    @Test
    @DisplayName("One task throwing doesn't stop the others in the same tick")
    void oneTaskFailing_doesNotStopOthers() {
        Task failing = newTask();
        Task healthy = newTask();
        when(taskRepository.findActiveNonTerminalWithAssignee(any())).thenReturn(List.of(failing, healthy));
        when(taskReminderService.getOrCreateSettings(failing)).thenThrow(new RuntimeException("boom"));
        when(taskReminderService.getOrCreateSettings(healthy))
                .thenReturn(settingWith(healthy, true, LocalTime.of(9, 0)));

        scheduler.checkReminders();

        verify(taskReminderService).processDueReminder(eq(healthy), any(TaskReminderSchedule.class), any());
    }

    @Test
    @DisplayName("A non-active schedule slot is never evaluated as due")
    void inactiveSchedule_isSkipped() {
        Task task = newTask();
        TaskReminderSetting setting = TaskReminderSetting.builder()
                .task(task).enabled(true).timezone(ZONE.getId()).build();
        setting.addSchedule(TaskReminderSchedule.builder().reminderTime(LocalTime.of(9, 0)).active(false).build());

        when(taskRepository.findActiveNonTerminalWithAssignee(any())).thenReturn(List.of(task));
        when(taskReminderService.getOrCreateSettings(task)).thenReturn(setting);

        scheduler.checkReminders();

        verify(taskReminderService, never()).processDueReminder(any(), any(), any());
    }

    @Test
    @DisplayName("Different timezone: a task configured for another zone fires at its own local time, not the server's")
    void differentTimezone_firesAtLocalTime() {
        // clock is fixed at 09:02 Asia/Phnom_Penh (UTC+7) == 02:02 UTC.
        // A task configured for UTC with a 02:00 schedule should be due at this instant.
        Task task = newTask();
        TaskReminderSetting utcSetting = TaskReminderSetting.builder()
                .task(task).enabled(true).timezone("UTC").build();
        utcSetting.addSchedule(TaskReminderSchedule.builder().reminderTime(LocalTime.of(2, 0)).active(true).build());

        when(taskRepository.findActiveNonTerminalWithAssignee(any())).thenReturn(List.of(task));
        when(taskReminderService.getOrCreateSettings(task)).thenReturn(utcSetting);

        scheduler.checkReminders();

        verify(taskReminderService).processDueReminder(eq(task), any(TaskReminderSchedule.class), eq(LocalDate.of(2026, 8, 31)));
    }
}
