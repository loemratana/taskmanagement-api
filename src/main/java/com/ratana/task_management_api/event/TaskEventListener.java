package com.ratana.task_management_api.event;

import com.ratana.task_management_api.dto.NotificationRequest;
import com.ratana.task_management_api.enums.NotificationChannel;
import com.ratana.task_management_api.enums.NotificationPriority;
import com.ratana.task_management_api.enums.NotificationType;
import com.ratana.task_management_api.service.DepartmentTelegramGroupService;
import com.ratana.task_management_api.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.Set;

/**
 * Converts committed domain events into notifications.
 * Uses AFTER_COMMIT so notifications never race uncommitted task rows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskEventListener {

    private final NotificationService notificationService;
    private final DepartmentTelegramGroupService departmentTelegramGroupService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTaskAssigned(TaskAssignedEvent event) {
        try {
            Set<NotificationChannel> forcedChannels = departmentTelegramGroupService
                    .resolveActive(event.getDepartmentId())
                    .map(group -> Set.of(NotificationChannel.TELEGRAM_GROUP))
                    .orElse(Set.of());

            notificationService.send(NotificationRequest.builder()
                    .recipientId(event.getAssigneeId())
                    .senderId(event.getAssignedBy())
                    .title("New Task Assigned")
                    .message(event.getAssignedByName() + " assigned you a new task: " + event.getTaskTitle())
                    .type(NotificationType.TASK_ASSIGNED)
                    .priority(NotificationPriority.HIGH)
                    .referenceType("TASK")
                    .referenceId(event.getTaskId())
                    .metadata(Map.of(
                            "taskTitle", event.getTaskTitle(),
                            "assignedBy", event.getAssignedBy().toString()
                    ))
                    .forcedChannels(forcedChannels)
                    .build());

            log.info("Notification sent → TaskAssigned | taskId={} | assigneeId={}",
                    event.getTaskId(), event.getAssigneeId());
        } catch (Exception e) {
            log.error("Failed to send TaskAssigned notification | taskId={}", event.getTaskId(), e);
        }
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTaskCommentAdded(TaskCommentAddedEvent event) {
        try {
            if (event.getRecipientId().equals(event.getCommenterId())) {
                return;
            }

            notificationService.send(NotificationRequest.builder()
                    .recipientId(event.getRecipientId())
                    .senderId(event.getCommenterId())
                    .title("New Comment on Task")
                    .message(event.getCommenterName() + " commented on \"" + event.getTaskTitle() + "\": "
                            + event.getCommentPreview())
                    .type(NotificationType.TASK_COMMENTED)
                    .priority(NotificationPriority.NORMAL)
                    .referenceType("TASK")
                    .referenceId(event.getTaskId())
                    .metadata(Map.of(
                            "commentPreview", event.getCommentPreview()
                    ))
                    .build());

            log.info("Notification sent → TaskCommentAdded | taskId={} | recipientId={}",
                    event.getTaskId(), event.getRecipientId());
        } catch (Exception e) {
            log.error("Failed to send TaskCommentAdded notification | taskId={}", event.getTaskId(), e);
        }
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTaskStatusChanged(TaskStatusChangedEvent event) {
        try {
            if (event.getRecipientId().equals(event.getChangedBy())) {
                return;
            }

            notificationService.send(NotificationRequest.builder()
                    .recipientId(event.getRecipientId())
                    .senderId(event.getChangedBy())
                    .title("Task Status Updated")
                    .message("Task \"" + event.getTaskTitle() + "\" status changed from "
                            + event.getOldStatus() + " → " + event.getNewStatus())
                    .type(NotificationType.TASK_UPDATED)
                    .priority(NotificationPriority.NORMAL)
                    .referenceType("TASK")
                    .referenceId(event.getTaskId())
                    .metadata(Map.of(
                            "oldStatus", event.getOldStatus(),
                            "newStatus", event.getNewStatus()
                    ))
                    .build());

            log.info("Notification sent → TaskStatusChanged | taskId={} | recipientId={}",
                    event.getTaskId(), event.getRecipientId());
        } catch (Exception e) {
            log.error("Failed to send TaskStatusChanged notification | taskId={}", event.getTaskId(), e);
        }
    }
}
