package com.ratana.task_management_api.task.event;

import com.ratana.task_management_api.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Hands committed task events to NotificationService.
 * AFTER_COMMIT: nothing is sent if the task transaction rolls back.
 * Async + catch-all: a notification failure can never fail or slow the task request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskEventListener {

    private final NotificationService notificationService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(TaskEvent event) {
        try {
            notificationService.handleTaskEvent(event);
        } catch (Exception e) {
            log.error("Task event notification failed | type={} taskId={} actorId={} assigneeId={} creatorId={} error={}",
                    event.type(), event.taskId(), event.actorId(), event.assigneeId(), event.creatorId(),
                    e.getMessage(), e);
        }
    }
}
