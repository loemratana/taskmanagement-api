package com.ratana.task_management_api.service.sender;

import com.ratana.task_management_api.enums.NotificationChannel;
import com.ratana.task_management_api.exception.NotificationDeliveryException;
import com.ratana.task_management_api.exception.TelegramApiException;
import com.ratana.task_management_api.model.DepartmentTelegramGroup;
import com.ratana.task_management_api.model.Notification;
import com.ratana.task_management_api.model.Task;
import com.ratana.task_management_api.repository.TaskRepository;
import com.ratana.task_management_api.service.DepartmentTelegramGroupService;
import com.ratana.task_management_api.service.NotificationSender;
import com.ratana.task_management_api.service.TelegramBotClient;
import com.ratana.task_management_api.service.TelegramMessageFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Broadcasts a task-assignment notification to the task's department Telegram group.
 * Routes off Task.department (stable regardless of who is currently assigned), not
 * the assignee's department.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramGroupNotificationSender implements NotificationSender {

    private final TaskRepository taskRepository;
    private final DepartmentTelegramGroupService groupService;
    private final TelegramMessageFormatter messageFormatter;
    private final TelegramBotClient telegramBotClient;

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.TELEGRAM_GROUP;
    }

    @Override
    public void send(Notification notification) {
        UUID taskId = notification.getReferenceId();
        if (taskId == null) {
            throw new NotificationDeliveryException(
                    "TELEGRAM_GROUP notification " + notification.getId() + " has no task reference");
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotificationDeliveryException("Task not found: " + taskId));

        UUID departmentId = task.getDepartment().getId();
        DepartmentTelegramGroup group = groupService.resolveActive(departmentId)
                .orElseThrow(() -> new NotificationDeliveryException(
                        "No active Telegram group configured for department " + departmentId));

        String text = messageFormatter.forTaskAssigned(task);

        try {
            telegramBotClient.sendMessage(group.getChatId(), text);
        } catch (TelegramApiException e) {
            if (!e.isRetryable()) {
                log.warn("Deactivating Telegram group {} for department {} after a permanent error: {}",
                        group.getId(), departmentId, e.getMessage());
                groupService.deactivate(group.getId());
            }
            throw e;
        }
    }
}
