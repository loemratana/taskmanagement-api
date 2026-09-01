package com.ratana.task_management_api.event;

import com.ratana.task_management_api.dto.NotificationRequest;
import com.ratana.task_management_api.enums.NotificationType;
import com.ratana.task_management_api.model.DepartmentTelegramGroup;
import com.ratana.task_management_api.service.DepartmentTelegramGroupService;
import com.ratana.task_management_api.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies TaskEventListener actually turns each domain event into a
 * NotificationService.send() call with the right recipient/type — i.e. that
 * "notification sent" claims for comment/assign/status-change are real, not
 * just wired-looking.
 */
@ExtendWith(MockitoExtension.class)
class TaskEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private DepartmentTelegramGroupService departmentTelegramGroupService;

    private TaskEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new TaskEventListener(notificationService, departmentTelegramGroupService);
    }

    @Test
    @DisplayName("Task assigned -> notification sent to assignee")
    void taskAssigned_sendsNotification() {
        UUID taskId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UUID assignedBy = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        when(departmentTelegramGroupService.resolveActive(departmentId)).thenReturn(Optional.empty());

        TaskAssignedEvent event = new TaskAssignedEvent(
                this, taskId, "Write report", assigneeId, assignedBy, "Alice", departmentId);

        listener.handleTaskAssigned(event);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).send(captor.capture());

        NotificationRequest sent = captor.getValue();
        assertThat(sent.getRecipientId()).isEqualTo(assigneeId);
        assertThat(sent.getSenderId()).isEqualTo(assignedBy);
        assertThat(sent.getType()).isEqualTo(NotificationType.TASK_ASSIGNED);
        assertThat(sent.getReferenceId()).isEqualTo(taskId);
    }

    @Test
    @DisplayName("Task commented -> notification sent to recipient (not the commenter)")
    void taskCommented_sendsNotification() {
        UUID taskId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID commenterId = UUID.randomUUID();

        TaskCommentAddedEvent event = new TaskCommentAddedEvent(
                this, taskId, "Write report", recipientId, commenterId, "Bob", "looks good");

        listener.handleTaskCommentAdded(event);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).send(captor.capture());

        NotificationRequest sent = captor.getValue();
        assertThat(sent.getRecipientId()).isEqualTo(recipientId);
        assertThat(sent.getSenderId()).isEqualTo(commenterId);
        assertThat(sent.getType()).isEqualTo(NotificationType.TASK_COMMENTED);
    }

    @Test
    @DisplayName("Task commented -> no self-notification when commenter is the recipient")
    void taskCommented_skipsSelfNotification() {
        UUID taskId = UUID.randomUUID();
        UUID sameUser = UUID.randomUUID();

        TaskCommentAddedEvent event = new TaskCommentAddedEvent(
                this, taskId, "Write report", sameUser, sameUser, "Bob", "note to self");

        listener.handleTaskCommentAdded(event);

        verify(notificationService, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Task status changed (e.g. approved) -> notification sent to recipient")
    void taskStatusChanged_sendsNotification() {
        UUID taskId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID changedBy = UUID.randomUUID();

        TaskStatusChangedEvent event = new TaskStatusChangedEvent(
                this, taskId, "Write report", recipientId, "IN_REVIEW", "APPROVED", changedBy, "Carol");

        listener.handleTaskStatusChanged(event);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).send(captor.capture());

        NotificationRequest sent = captor.getValue();
        assertThat(sent.getRecipientId()).isEqualTo(recipientId);
        assertThat(sent.getType()).isEqualTo(NotificationType.TASK_UPDATED);
        assertThat(sent.getMessage()).contains("IN_REVIEW").contains("APPROVED");
    }

    @Test
    @DisplayName("Task status changed -> no self-notification when the actor changed their own task")
    void taskStatusChanged_skipsSelfNotification() {
        UUID taskId = UUID.randomUUID();
        UUID sameUser = UUID.randomUUID();

        TaskStatusChangedEvent event = new TaskStatusChangedEvent(
                this, taskId, "Write report", sameUser, "TODO", "IN_PROGRESS", sameUser, "Dave");

        listener.handleTaskStatusChanged(event);

        verify(notificationService, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Task assigned -> forces TELEGRAM_GROUP channel when department has an active group")
    void taskAssigned_forcesTelegramGroupChannel() {
        UUID taskId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UUID assignedBy = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        DepartmentTelegramGroup group = new DepartmentTelegramGroup();
        when(departmentTelegramGroupService.resolveActive(departmentId)).thenReturn(Optional.of(group));

        TaskAssignedEvent event = new TaskAssignedEvent(
                this, taskId, "Write report", assigneeId, assignedBy, "Alice", departmentId);

        listener.handleTaskAssigned(event);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).send(captor.capture());
        assertThat(captor.getValue().getForcedChannels())
                .contains(com.ratana.task_management_api.enums.NotificationChannel.TELEGRAM_GROUP);
    }
}
