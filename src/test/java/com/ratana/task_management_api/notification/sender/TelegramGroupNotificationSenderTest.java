package com.ratana.task_management_api.notification.sender;

import com.ratana.task_management_api.notification.enums.NotificationType;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.organization.entity.DepartmentTelegramGroup;
import com.ratana.task_management_api.notification.entity.Notification;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.entity.TaskSubmission;
import com.ratana.task_management_api.task.repository.TaskRepository;
import com.ratana.task_management_api.task.repository.TaskSubmissionRepository;
import com.ratana.task_management_api.organization.service.DepartmentTelegramGroupService;
import com.ratana.task_management_api.infrastructure.storage.StorageService;
import com.ratana.task_management_api.infrastructure.messaging.TelegramBotClient;
import com.ratana.task_management_api.infrastructure.messaging.TelegramMessageFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionOperations;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Telegram threading: assigned -> submitted (reply) -> reviewed (reply). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramGroupNotificationSenderTest {

    private static final String CHAT = "-100123";

    @Mock private TaskRepository taskRepository;
    @Mock private TaskSubmissionRepository submissionRepository;
    @Mock private StorageService storageService;
    @Mock private DepartmentTelegramGroupService groupService;
    @Mock private TelegramMessageFormatter formatter;
    @Mock private TelegramBotClient client;
    private TelegramGroupNotificationSender sender;

    private Task task;
    private TaskSubmission submission;

    @BeforeEach
    void setUp() {
        sender = new TelegramGroupNotificationSender(taskRepository, submissionRepository, storageService,
                groupService, formatter, client, TransactionOperations.withoutTransaction());

        Department department = new Department();
        department.setId(UUID.randomUUID());
        task = Task.builder().id(UUID.randomUUID()).title("Fix printer").department(department).build();
        submission = TaskSubmission.builder().id(UUID.randomUUID()).task(task).round(1).build();

        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(submissionRepository.findByIdAndTaskId(submission.getId(), task.getId()))
                .thenReturn(Optional.of(submission));
        when(groupService.resolveActive(department.getId()))
                .thenReturn(Optional.of(DepartmentTelegramGroup.builder().chatId(CHAT).build()));
        when(formatter.forTaskAssigned(any())).thenReturn("assigned");
        when(formatter.forTaskSubmitted(any())).thenReturn("submitted");
        when(formatter.forTaskReviewed(any())).thenReturn("reviewed");
    }

    private Notification notification(NotificationType type, boolean withSubmission) {
        Map<String, Object> metadata = new HashMap<>();
        if (withSubmission) {
            metadata.put("submissionId", submission.getId().toString());
        }
        return Notification.builder().id(UUID.randomUUID()).type(type)
                .referenceId(task.getId()).metadata(metadata).build();
    }

    @Test
    @DisplayName("assignment is a new message and its id is saved on the task")
    void assignment_savesMessageId() {
        when(client.sendMessage(CHAT, "assigned", null)).thenReturn(100L);

        sender.send(notification(NotificationType.TASK_ASSIGNED, false));

        verify(taskRepository).saveTelegramMessage(task.getId(), CHAT, 100L);
    }

    @Test
    @DisplayName("submission replies to the assignment message and its id is saved on the submission")
    void submission_repliesToAssignment() {
        task.setTelegramChatId(CHAT);
        task.setTelegramMessageId(100L);
        when(client.sendMessage(CHAT, "submitted", 100L)).thenReturn(200L);

        sender.send(notification(NotificationType.APPROVAL_REQUEST, true));

        verify(client).sendMessage(CHAT, "submitted", 100L);
        verify(submissionRepository).saveTelegramMessage(submission.getId(), CHAT, 200L);
    }

    @Test
    @DisplayName("no reply when the assignment was posted in a different group")
    void submission_otherChat_noReply() {
        task.setTelegramChatId("-100999");
        task.setTelegramMessageId(100L);
        when(client.sendMessage(CHAT, "submitted", null)).thenReturn(200L);

        sender.send(notification(NotificationType.APPROVAL_REQUEST, true));

        verify(client).sendMessage(CHAT, "submitted", null);
    }

    @Test
    @DisplayName("approval replies to the submission message and saves nothing")
    void review_repliesToSubmission() {
        task.setTelegramChatId(CHAT);
        task.setTelegramMessageId(100L);
        submission.setTelegramChatId(CHAT);
        submission.setTelegramMessageId(200L);

        sender.send(notification(NotificationType.APPROVAL_APPROVED, true));

        verify(client).sendMessage(CHAT, "reviewed", 200L);
        verify(formatter, never()).forTaskSubmitted(any());
        verify(submissionRepository, never()).saveTelegramMessage(any(), anyString(), anyLong());
    }

    @Test
    @DisplayName("rejection falls back to replying to the assignment if the submission wasn't posted")
    void review_fallsBackToAssignment() {
        task.setTelegramChatId(CHAT);
        task.setTelegramMessageId(100L);

        sender.send(notification(NotificationType.APPROVAL_REJECTED, true));

        verify(client).sendMessage(CHAT, "reviewed", 100L);
    }

    @Test
    @DisplayName("review with nothing to reply to is posted as a plain message")
    void review_nothingStored_plainMessage() {
        sender.send(notification(NotificationType.APPROVAL_APPROVED, true));

        verify(client).sendMessage(eq(CHAT), eq("reviewed"), isNull());
    }
}
