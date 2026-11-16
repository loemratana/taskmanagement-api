package com.ratana.task_management_api.notification.service;

import com.ratana.task_management_api.notification.dto.request.NotificationRequest;
import com.ratana.task_management_api.notification.enums.NotificationChannel;
import com.ratana.task_management_api.notification.enums.NotificationType;
import com.ratana.task_management_api.task.event.TaskEvent;
import com.ratana.task_management_api.task.event.TaskEventType;
import com.ratana.task_management_api.organization.entity.DepartmentTelegramGroup;
import com.ratana.task_management_api.notification.repository.NotificationDeliveryRepository;
import com.ratana.task_management_api.notification.repository.NotificationRepository;
import com.ratana.task_management_api.organization.service.DepartmentTelegramGroupService;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task notification rules in NotificationServiceImpl.handleTaskEvent: who is notified,
 * what they are told, and when nobody is. send() is stubbed — its delivery pipeline
 * is not under test here.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationDeliveryRepository deliveryRepository;
    @Mock private NotificationPreferenceService preferenceService;
    @Mock private NotificationDispatcher dispatcher;
    @Mock private DepartmentTelegramGroupService departmentTelegramGroupService;
    @Mock private UserRepository userRepository;

    private NotificationServiceImpl service;

    private final UUID taskId = UUID.randomUUID();
    private final UUID departmentId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID assigneeId = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = spy(new NotificationServiceImpl(notificationRepository, deliveryRepository,
                preferenceService, dispatcher, departmentTelegramGroupService, userRepository));
    }

    @Test
    @DisplayName("Task assigned -> notification sent to assignee")
    void taskAssigned_notifiesAssignee() {
        when(departmentTelegramGroupService.resolveActive(departmentId)).thenReturn(Optional.empty());

        NotificationRequest sent = handleAndCapture(assigned(assigneeId));

        assertThat(sent.getRecipientId()).isEqualTo(assigneeId);
        assertThat(sent.getSenderId()).isEqualTo(actorId);
        assertThat(sent.getType()).isEqualTo(NotificationType.TASK_ASSIGNED);
        assertThat(sent.getReferenceId()).isEqualTo(taskId);
        assertThat(sent.getMessage()).isEqualTo("Alice assigned you a new task: Write report");
        assertThat(sent.getForcedChannels()).isEmpty();
    }

    @Test
    @DisplayName("Task assigned -> forces TELEGRAM_GROUP when the department has an active group")
    void taskAssigned_forcesTelegramGroupChannel() {
        when(departmentTelegramGroupService.resolveActive(departmentId))
                .thenReturn(Optional.of(new DepartmentTelegramGroup()));

        NotificationRequest sent = handleAndCapture(assigned(assigneeId));

        assertThat(sent.getForcedChannels()).containsExactly(NotificationChannel.TELEGRAM_GROUP);
    }

    @Test
    @DisplayName("Task self-assigned -> still notified (unchanged behaviour; keeps the Telegram group broadcast)")
    void taskSelfAssigned_stillNotifies() {
        when(departmentTelegramGroupService.resolveActive(departmentId)).thenReturn(Optional.empty());

        NotificationRequest sent = handleAndCapture(assigned(actorId));

        assertThat(sent.getRecipientId()).isEqualTo(actorId);
    }

    @Test
    @DisplayName("Comment added -> notifies the assignee and the creator")
    void commentAdded_notifiesAssigneeAndCreator() {
        List<NotificationRequest> sent = handleAndCaptureAll(comment(assigneeId, creatorId, "looks good"));

        assertThat(sent).extracting(NotificationRequest::getRecipientId).containsExactly(assigneeId, creatorId);
        assertThat(sent.get(0).getSenderId()).isEqualTo(actorId);
        assertThat(sent.get(0).getType()).isEqualTo(NotificationType.TASK_COMMENTED);
        assertThat(sent.get(0).getMessage()).isEqualTo("Alice commented on \"Write report\": looks good");
    }

    @Test
    @DisplayName("Comment on a shared task -> every collaborator is notified too, never the author")
    void commentOnSharedTask_notifiesCollaborators() {
        UUID collaborator = UUID.randomUUID();
        TaskEvent event = new TaskEvent(TaskEventType.COMMENT_ADDED, taskId, "Write report", departmentId,
                actorId, "Alice", assigneeId, creatorId, null, null, "update?", null, null, null, null,
                List.of(collaborator, actorId));

        List<NotificationRequest> sent = handleAndCaptureAll(event);

        assertThat(sent).extracting(NotificationRequest::getRecipientId)
                .containsExactly(assigneeId, collaborator, creatorId);
    }

    @Test
    @DisplayName("Comment by the assignee -> notifies the creator instead")
    void commentByAssignee_notifiesCreator() {
        NotificationRequest sent = handleAndCapture(comment(actorId, creatorId, "done"));

        assertThat(sent.getRecipientId()).isEqualTo(creatorId);
    }

    @Test
    @DisplayName("Comment on an unassigned task -> notifies the creator")
    void commentOnUnassignedTask_notifiesCreator() {
        NotificationRequest sent = handleAndCapture(comment(null, creatorId, "any update?"));

        assertThat(sent.getRecipientId()).isEqualTo(creatorId);
    }

    @Test
    @DisplayName("Comment on your own task -> nobody notified")
    void commentOnOwnTask_skipsNotification() {
        service.handleTaskEvent(comment(actorId, actorId, "note to self"));

        verify(service, never()).send(any());
    }

    @Test
    @DisplayName("Long comment -> preview truncated to 140 characters")
    void commentAdded_truncatesPreview() {
        NotificationRequest sent = handleAndCaptureAll(comment(assigneeId, creatorId, "x".repeat(200))).get(0);

        assertThat(sent.getMetadata().get("commentPreview")).isEqualTo("x".repeat(140) + "…");
    }

    @Test
    @DisplayName("Status changed -> notifies the assignee")
    void statusChanged_notifiesAssignee() {
        NotificationRequest sent = handleAndCapture(statusChanged(assigneeId));

        assertThat(sent.getRecipientId()).isEqualTo(assigneeId);
        assertThat(sent.getType()).isEqualTo(NotificationType.TASK_UPDATED);
        assertThat(sent.getMessage()).isEqualTo("Alice moved \"Write report\" from In review to Approved");
    }

    @Test
    @DisplayName("Status changed by the assignee -> nobody notified")
    void statusChangedByAssignee_skipsNotification() {
        service.handleTaskEvent(statusChanged(actorId));

        verify(service, never()).send(any());
    }

    @Test
    @DisplayName("Status changed on an unassigned task -> nobody notified")
    void statusChangedOnUnassignedTask_skipsNotification() {
        service.handleTaskEvent(statusChanged(null));

        verify(service, never()).send(any());
    }

    @Test
    @DisplayName("Shared task approved -> submitter gets the review, the other assignees a completion notice")
    void sharedTaskReviewed_notifiesSubmitterAndOthers() {
        UUID collaborator = UUID.randomUUID();
        UUID otherCollaborator = UUID.randomUUID();
        when(departmentTelegramGroupService.resolveActive(departmentId)).thenReturn(Optional.empty());

        List<NotificationRequest> sent = handleAndCaptureAll(reviewed(collaborator, "DONE",
                List.of(collaborator, otherCollaborator)));

        assertThat(sent).hasSize(3);
        assertThat(sent.get(0).getRecipientId()).isEqualTo(collaborator);
        assertThat(sent.get(0).getType()).isEqualTo(NotificationType.APPROVAL_APPROVED);
        assertThat(sent.get(0).getMessage()).isEqualTo("Alice approved your work on \"Write report\"");
        assertThat(sent.subList(1, 3)).extracting(NotificationRequest::getRecipientId)
                .containsExactly(assigneeId, otherCollaborator);
        assertThat(sent.get(1).getTitle()).isEqualTo("Shared Task Completed");
        assertThat(sent.get(1).getForcedChannels()).isNullOrEmpty();
    }

    @Test
    @DisplayName("Single-assignee task reviewed -> only the assignee is notified (unchanged)")
    void singleTaskReviewed_notifiesAssigneeOnly() {
        when(departmentTelegramGroupService.resolveActive(departmentId)).thenReturn(Optional.empty());

        List<NotificationRequest> sent = handleAndCaptureAll(reviewed(assigneeId, "REJECTED", List.of()));

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getRecipientId()).isEqualTo(assigneeId);
        assertThat(sent.get(0).getType()).isEqualTo(NotificationType.APPROVAL_REJECTED);
    }

    @Test
    @DisplayName("Collaborators added -> each new collaborator is notified personally, never the actor")
    void collaboratorsAdded_notifiesEach() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        TaskEvent event = new TaskEvent(TaskEventType.COLLABORATOR_ADDED, taskId, "Write report", departmentId,
                actorId, "Alice", assigneeId, creatorId, null, null, null, null, null, null, null,
                List.of(first, actorId, second));

        List<NotificationRequest> sent = handleAndCaptureAll(event);

        assertThat(sent).extracting(NotificationRequest::getRecipientId).containsExactly(first, second);
        assertThat(sent.get(0).getType()).isEqualTo(NotificationType.TASK_ASSIGNED);
        assertThat(sent.get(0).getForcedChannels()).isNullOrEmpty();
    }

    @Test
    @DisplayName("Assignee blocks a task -> the creator and the assignee's manager are told, with the reason")
    void blockedByAssignee_notifiesCreatorAndManager() {
        UUID managerId = givenManagerOf(actorId);
        TaskEvent event = new TaskEvent(TaskEventType.STATUS_CHANGED, taskId, "Write report", departmentId,
                actorId, "Dara", actorId, creatorId, "IN_PROGRESS", "BLOCKED", "Waiting for the printer",
                null, null, null, null, List.of());

        List<NotificationRequest> sent = handleAndCaptureAll(event);

        assertThat(sent).extracting(NotificationRequest::getRecipientId).containsExactly(creatorId, managerId);
        assertThat(sent.get(0).getTitle()).isEqualTo("Task Blocked");
        assertThat(sent.get(0).getMessage()).isEqualTo("🚫 Dara blocked \"Write report\": Waiting for the printer");
    }

    @Test
    @DisplayName("Assignee simply starts a task -> nobody is told")
    void startedByAssignee_skipsNotification() {
        TaskEvent event = new TaskEvent(TaskEventType.STATUS_CHANGED, taskId, "Write report", departmentId,
                actorId, "Dara", actorId, creatorId, "TODO", "IN_PROGRESS", null, null, null, null, null, List.of());

        service.handleTaskEvent(event);

        verify(service, never()).send(any());
    }

    @Test
    @DisplayName("Manager cancels a shared task -> assignee and collaborators get a readable message")
    void cancelledByManager_notifiesEveryoneOnTask() {
        UUID collaborator = UUID.randomUUID();
        TaskEvent event = new TaskEvent(TaskEventType.STATUS_CHANGED, taskId, "Write report", departmentId,
                actorId, "Alice", assigneeId, creatorId, "IN_PROGRESS", "CANCELLED", "Client dropped it",
                null, null, null, null, List.of(collaborator));

        List<NotificationRequest> sent = handleAndCaptureAll(event);

        assertThat(sent).extracting(NotificationRequest::getRecipientId).containsExactly(assigneeId, collaborator);
        assertThat(sent.get(0).getTitle()).isEqualTo("Task Cancelled");
        assertThat(sent.get(0).getMessage()).isEqualTo("Alice cancelled \"Write report\": Client dropped it");
    }

    @Test
    @DisplayName("Submitted -> the creator and the assignee's manager both review; Telegram group posted once")
    void submitted_notifiesCreatorAndManager_groupOnce() {
        UUID managerId = givenManagerOf(actorId);
        when(departmentTelegramGroupService.resolveActive(departmentId))
                .thenReturn(Optional.of(new DepartmentTelegramGroup()));
        TaskEvent event = new TaskEvent(TaskEventType.SUBMITTED, taskId, "Write report", departmentId,
                actorId, "Dara", actorId, creatorId, "IN_PROGRESS", "IN_REVIEW", null,
                UUID.randomUUID(), null, null, actorId, List.of());

        List<NotificationRequest> sent = handleAndCaptureAll(event);

        assertThat(sent).extracting(NotificationRequest::getRecipientId).containsExactly(creatorId, managerId);
        assertThat(sent).allMatch(request -> request.getType() == NotificationType.APPROVAL_REQUEST);
        assertThat(sent.get(0).getForcedChannels()).containsExactly(NotificationChannel.TELEGRAM_GROUP);
        assertThat(sent.get(1).getAllowedChannels()).doesNotContain(NotificationChannel.TELEGRAM_GROUP);
    }

    @Test
    @DisplayName("Due date changed -> everyone on the task is told the old and new date")
    void detailsChanged_notifiesPeopleOnTask() {
        TaskEvent event = new TaskEvent(TaskEventType.DETAILS_CHANGED, taskId, "Write report", departmentId,
                actorId, "Alice", assigneeId, creatorId, null, null, "due date 20 Nov 2026 → 25 Nov 2026",
                null, null, null, null, List.of());

        NotificationRequest sent = handleAndCapture(event);

        assertThat(sent.getRecipientId()).isEqualTo(assigneeId);
        assertThat(sent.getMessage())
                .isEqualTo("📅 Alice changed \"Write report\": due date 20 Nov 2026 → 25 Nov 2026");
    }

    private UUID givenManagerOf(UUID userId) {
        User manager = new User();
        manager.setId(UUID.randomUUID());
        User employee = new User();
        employee.setId(userId);
        employee.setManager(manager);
        when(userRepository.findById(userId)).thenReturn(Optional.of(employee));
        return manager.getId();
    }

    private List<NotificationRequest> handleAndCaptureAll(TaskEvent event) {
        doReturn(null).when(service).send(any());
        service.handleTaskEvent(event);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(service, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
        return captor.getAllValues();
    }

    private TaskEvent reviewed(UUID submittedBy, String newStatus, List<UUID> collaborators) {
        return new TaskEvent(TaskEventType.REVIEWED, taskId, "Write report", departmentId,
                actorId, "Alice", assigneeId, creatorId, "IN_REVIEW", newStatus, null,
                UUID.randomUUID(), null, null, submittedBy, collaborators);
    }

    private NotificationRequest handleAndCapture(TaskEvent event) {
        doReturn(null).when(service).send(any());
        service.handleTaskEvent(event);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(service).send(captor.capture());
        return captor.getValue();
    }

    private TaskEvent assigned(UUID assignee) {
        return new TaskEvent(TaskEventType.ASSIGNED, taskId, "Write report", departmentId,
                actorId, "Alice", assignee, creatorId, null, null, null, null, null, null, null, null);
    }

    private TaskEvent comment(UUID assignee, UUID creator, String content) {
        return new TaskEvent(TaskEventType.COMMENT_ADDED, taskId, "Write report", departmentId,
                actorId, "Alice", assignee, creator, null, null, content, null, null, null, null, null);
    }

    private TaskEvent statusChanged(UUID assignee) {
        return new TaskEvent(TaskEventType.STATUS_CHANGED, taskId, "Write report", departmentId,
                actorId, "Alice", assignee, creatorId, "IN_REVIEW", "APPROVED", null, null, null, null, null, null);
    }
}
