package com.ratana.task_management_api.notification.sender;

import com.ratana.task_management_api.notification.enums.NotificationChannel;
import com.ratana.task_management_api.notification.entity.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * IN_APP delivery: the persisted notification row is the inbox entry, so there is nothing
 * to push. Clients read it via GET /api/v1/notifications and the unread-count endpoint.
 */
@Slf4j
@Component
public class InAppNotificationSender implements NotificationSender {

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public void send(Notification notification) {
        log.debug("In-app notification {} stored for user {}",
                notification.getId(), notification.getRecipientId());
    }
}
