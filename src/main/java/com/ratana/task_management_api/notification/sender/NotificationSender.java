package com.ratana.task_management_api.notification.sender;

import com.ratana.task_management_api.notification.enums.NotificationChannel;
import com.ratana.task_management_api.notification.entity.Notification;

/**
 * Strategy for delivering a {@link Notification} over one {@link NotificationChannel}.
 * Each implementation is a Spring bean; {@link com.ratana.task_management_api.notification.service.NotificationDispatcher}
 * collects them and picks the strategy matching each delivery's channel.
 * Add a new channel by adding an enum value and a new implementation — the dispatcher needs no change.
 */
public interface NotificationSender {

    NotificationChannel getChannel();

    void send(Notification notification);
}
