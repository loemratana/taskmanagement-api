package com.ratana.task_management_api.services;

import com.ratana.task_management_api.dto.NotificationRequest;
import com.ratana.task_management_api.dto.request.ReminderSettingsUpdateRequest;
import com.ratana.task_management_api.dto.response.NotificationResponse;
import com.ratana.task_management_api.dto.response.ReminderSettingsResponse;
import com.ratana.task_management_api.enums.NotificationType;
import com.ratana.task_management_api.enums.Priority;
import com.ratana.task_management_api.enums.ReminderLogStatus;
import com.ratana.task_management_api.enums.TaskStatus;
import com.ratana.task_management_api.model.Task;
import com.ratana.task_management_api.model.TaskReminderLog;
import com.ratana.task_management_api.model.TaskReminderSchedule;
import com.ratana.task_management_api.model.TaskReminderSetting;
import com.ratana.task_management_api.model.User;
import com.ratana.task_management_api.repository.TaskReminderLogRepository;
import com.ratana.task_management_api.repository.TaskReminderSettingRepository;
import com.ratana.task_management_api.repository.TaskRepository;
import com.ratana.task_management_api.security.UserPrincipal;
import com.ratana.task_management_api.service.NotificationService;
import com.ratana.task_management_api.service.ServiceImpl.TaskAuthorizationService;
import com.ratana.task_management_api.service.ServiceImpl.TaskReminderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskReminderServiceImplTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TaskReminderSettingRepository settingRepository;
    @Mock private TaskReminderLogRepository logRepository;
    @Mock private TaskAuthorizationService taskAuthorizationService;
    @Mock private NotificationService notificationService;

    private TaskReminderServiceImpl service;

    private UUID taskId;
    private UUID assigneeId;
    private UUID assignedById;
    private Task task;
    private UserPrincipal actor;

    @BeforeEach
    void setUp() {
        service = new TaskReminderServiceImpl(
                taskRepository, settingRepository, logRepository, taskAuthorizationService, notificationService);
        ReflectionTestUtils.setField(service, "defaultTimezone", "Asia/Phnom_Penh");
        ReflectionTestUtils.setField(service, "defaultTimesCsv", "09:00,13:00,17:00");

        taskId = UUID.randomUUID();
        assigneeId = UUID.randomUUID();
        assignedById = UUID.randomUUID();

        User assignee = new User();
        assignee.setId(assigneeId);
        User assignedBy = new User();
        assignedBy.setId(assignedById);

        task = new Task();
        task.setId(taskId);
        task.setTitle("Prepare monthly report");
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setPriority(Priority.HIGH);
        task.setActive(true);
        task.setAssignee(assignee);
        task.setAssignedBy(assignedBy);
        task.setDueDate(LocalDate.now());

        actor = mock(UserPrincipal.class);
    }

    // ---- getOrCreateSettings ----

    @Test
    @DisplayName("getOrCreateSettings – provisions default settings with 3 schedules when none exist")
    void getOrCreateSettings_provisionsDefaults() {
        when(settingRepository.findByTaskId(taskId)).thenReturn(Optional.empty());
        when(settingRepository.saveAndFlush(any(TaskReminderSetting.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TaskReminderSetting setting = service.getOrCreateSettings(task);

        assertThat(setting.isEnabled()).isTrue();
        assertThat(setting.getTimezone()).isEqualTo("Asia/Phnom_Penh");
        assertThat(setting.getSchedules()).extracting(TaskReminderSchedule::getReminderTime)
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(13, 0), LocalTime.of(17, 0));
    }

    @Test
    @DisplayName("getOrCreateSettings – returns existing settings without creating a new one")
    void getOrCreateSettings_returnsExisting() {
        TaskReminderSetting existing = TaskReminderSetting.builder().task(task).enabled(true).timezone("UTC").build();
        when(settingRepository.findByTaskId(taskId)).thenReturn(Optional.of(existing));

        TaskReminderSetting result = service.getOrCreateSettings(task);

        assertThat(result).isSameAs(existing);
        verify(settingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("getOrCreateSettings – concurrent provisioning race falls back to the winner's row")
    void getOrCreateSettings_handlesConcurrentProvisioningRace() {
        TaskReminderSetting winner = TaskReminderSetting.builder().task(task).enabled(true).timezone("UTC").build();
        when(settingRepository.findByTaskId(taskId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(settingRepository.saveAndFlush(any(TaskReminderSetting.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        TaskReminderSetting result = service.getOrCreateSettings(task);

        assertThat(result).isSameAs(winner);
    }

    // ---- getSettings ----

    @Test
    @DisplayName("getSettings – returns computed defaults without persisting when no row exists")
    void getSettings_returnsComputedDefaults_noWrite() {
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        doNothing().when(taskAuthorizationService).assertCanView(actor, task);
        when(settingRepository.findByTaskId(taskId)).thenReturn(Optional.empty());

        ReminderSettingsResponse response = service.getSettings(taskId, actor);

        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getTimezone()).isEqualTo("Asia/Phnom_Penh");
        assertThat(response.getReminderTimes())
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(13, 0), LocalTime.of(17, 0));
        verifyNoInteractions(logRepository);
        verify(settingRepository, never()).save(any());
        verify(settingRepository, never()).saveAndFlush(any());
    }

    // ---- updateSettings ----

    @Test
    @DisplayName("updateSettings – partial update only changes provided fields")
    void updateSettings_partialUpdate() {
        TaskReminderSetting existing = TaskReminderSetting.builder()
                .task(task).enabled(true).timezone("Asia/Phnom_Penh").build();
        existing.addSchedule(TaskReminderSchedule.builder().reminderTime(LocalTime.of(9, 0)).active(true).build());

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        doNothing().when(taskAuthorizationService).assertCanUpdate(actor, task);
        when(settingRepository.findByTaskId(taskId)).thenReturn(Optional.of(existing));
        when(settingRepository.save(any(TaskReminderSetting.class))).thenAnswer(inv -> inv.getArgument(0));

        ReminderSettingsUpdateRequest request = ReminderSettingsUpdateRequest.builder()
                .enabled(false)
                .build();

        ReminderSettingsResponse response = service.updateSettings(taskId, request, actor);

        assertThat(response.isEnabled()).isFalse();
        assertThat(response.getTimezone()).isEqualTo("Asia/Phnom_Penh");
        assertThat(response.getReminderTimes()).containsExactly(LocalTime.of(9, 0));
    }

    @Test
    @DisplayName("updateSettings – replacing reminder times dedupes and replaces the schedule list")
    void updateSettings_replacesReminderTimes_deduped() {
        TaskReminderSetting existing = TaskReminderSetting.builder()
                .task(task).enabled(true).timezone("Asia/Phnom_Penh").build();
        existing.addSchedule(TaskReminderSchedule.builder().reminderTime(LocalTime.of(9, 0)).active(true).build());

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        doNothing().when(taskAuthorizationService).assertCanUpdate(actor, task);
        when(settingRepository.findByTaskId(taskId)).thenReturn(Optional.of(existing));
        when(settingRepository.save(any(TaskReminderSetting.class))).thenAnswer(inv -> inv.getArgument(0));

        ReminderSettingsUpdateRequest request = ReminderSettingsUpdateRequest.builder()
                .reminderTimes(List.of(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(8, 0)))
                .build();

        ReminderSettingsResponse response = service.updateSettings(taskId, request, actor);

        assertThat(response.getReminderTimes()).containsExactly(LocalTime.of(8, 0), LocalTime.of(12, 0));
    }

    @Test
    @DisplayName("updateSettings – invalid timezone is rejected")
    void updateSettings_rejectsInvalidTimezone() {
        TaskReminderSetting existing = TaskReminderSetting.builder()
                .task(task).enabled(true).timezone("Asia/Phnom_Penh").build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        doNothing().when(taskAuthorizationService).assertCanUpdate(actor, task);
        when(settingRepository.findByTaskId(taskId)).thenReturn(Optional.of(existing));

        ReminderSettingsUpdateRequest request = ReminderSettingsUpdateRequest.builder()
                .timezone("Not/AZone")
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.updateSettings(taskId, request, actor))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- processDueReminder ----

    @Test
    @DisplayName("processDueReminder – eligible task sends a notification and records a SENT log")
    void processDueReminder_sendsNotification() {
        TaskReminderSchedule schedule = newSchedule(LocalTime.of(9, 0));
        LocalDate today = LocalDate.now();

        when(logRepository.existsByTaskIdAndScheduleIdAndReminderDate(taskId, schedule.getId(), today))
                .thenReturn(false);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(logRepository.saveAndFlush(any(TaskReminderLog.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID notificationId = UUID.randomUUID();
        when(notificationService.send(any(NotificationRequest.class)))
                .thenReturn(NotificationResponse.builder().id(notificationId).build());

        service.processDueReminder(task, schedule, today);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).send(captor.capture());
        NotificationRequest sent = captor.getValue();
        assertThat(sent.getRecipientId()).isEqualTo(assigneeId);
        assertThat(sent.getSenderId()).isEqualTo(assignedById);
        assertThat(sent.getType()).isEqualTo(NotificationType.TASK_REMINDER);
        assertThat(sent.getMessage()).contains("Prepare monthly report").contains("still incomplete");

        ArgumentCaptor<TaskReminderLog> logCaptor = ArgumentCaptor.forClass(TaskReminderLog.class);
        verify(logRepository, atLeastOnce()).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo(ReminderLogStatus.SENT);
        assertThat(logCaptor.getValue().getNotificationId()).isEqualTo(notificationId);
    }

    @Test
    @DisplayName("processDueReminder – already logged today is a no-op")
    void processDueReminder_alreadyLogged_noOp() {
        TaskReminderSchedule schedule = newSchedule(LocalTime.of(9, 0));
        LocalDate today = LocalDate.now();

        when(logRepository.existsByTaskIdAndScheduleIdAndReminderDate(taskId, schedule.getId(), today))
                .thenReturn(true);

        service.processDueReminder(task, schedule, today);

        verifyNoInteractions(notificationService);
        verify(taskRepository, never()).findById(any());
    }

    @Test
    @DisplayName("processDueReminder – task completed before the slot fires writes a SKIPPED log, no notification")
    void processDueReminder_taskCompleted_skips() {
        TaskReminderSchedule schedule = newSchedule(LocalTime.of(17, 0));
        LocalDate today = LocalDate.now();

        Task doneTask = new Task();
        doneTask.setId(taskId);
        doneTask.setStatus(TaskStatus.DONE);
        doneTask.setActive(true);
        doneTask.setAssignee(task.getAssignee());

        when(logRepository.existsByTaskIdAndScheduleIdAndReminderDate(taskId, schedule.getId(), today))
                .thenReturn(false);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(doneTask));
        when(logRepository.saveAndFlush(any(TaskReminderLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processDueReminder(task, schedule, today);

        verifyNoInteractions(notificationService);
        ArgumentCaptor<TaskReminderLog> captor = ArgumentCaptor.forClass(TaskReminderLog.class);
        verify(logRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReminderLogStatus.SKIPPED);
    }

    @Test
    @DisplayName("processDueReminder – concurrent claim (unique constraint hit) backs off quietly")
    void processDueReminder_concurrentClaim_backsOff() {
        TaskReminderSchedule schedule = newSchedule(LocalTime.of(9, 0));
        LocalDate today = LocalDate.now();

        when(logRepository.existsByTaskIdAndScheduleIdAndReminderDate(taskId, schedule.getId(), today))
                .thenReturn(false);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(logRepository.saveAndFlush(any(TaskReminderLog.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        service.processDueReminder(task, schedule, today);

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("processDueReminder – notification send failure marks the log FAILED (slot still claimed)")
    void processDueReminder_notificationFailure_marksFailed() {
        TaskReminderSchedule schedule = newSchedule(LocalTime.of(9, 0));
        LocalDate today = LocalDate.now();

        when(logRepository.existsByTaskIdAndScheduleIdAndReminderDate(taskId, schedule.getId(), today))
                .thenReturn(false);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(logRepository.saveAndFlush(any(TaskReminderLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notificationService.send(any(NotificationRequest.class)))
                .thenThrow(new RuntimeException("boom"));

        service.processDueReminder(task, schedule, today);

        ArgumentCaptor<TaskReminderLog> logCaptor = ArgumentCaptor.forClass(TaskReminderLog.class);
        verify(logRepository, atLeastOnce()).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo(ReminderLogStatus.FAILED);
    }

    /** TaskReminderSchedule's plain @Builder doesn't expose the inherited BaseEnitty id — set it via the setter. */
    private TaskReminderSchedule newSchedule(LocalTime time) {
        TaskReminderSchedule schedule = TaskReminderSchedule.builder().reminderTime(time).active(true).build();
        schedule.setId(UUID.randomUUID());
        return schedule;
    }
}
