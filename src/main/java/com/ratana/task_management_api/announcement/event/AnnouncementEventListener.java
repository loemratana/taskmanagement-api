package com.ratana.task_management_api.announcement.event;

import com.ratana.task_management_api.announcement.service.AnnouncementNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Fans out notifications for a committed publish.
 * AFTER_COMMIT: nothing is sent if the publish rolls back.
 * Async + catch-all: a large audience never slows or fails the publish request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementEventListener {

    private final AnnouncementNotifier announcementNotifier;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AnnouncementPublishedEvent event) {
        try {
            announcementNotifier.notifyRecipients(event.announcementId(), event.actorId());
        } catch (Exception e) {
            log.error("Announcement notification failed | announcementId={} actorId={} error={}",
                    event.announcementId(), event.actorId(), e.getMessage(), e);
        }
    }
}
