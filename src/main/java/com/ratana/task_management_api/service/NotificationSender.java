package com.ratana.task_management_api.service;

import com.ratana.task_management_api.enums.NotificationChannel;
import com.ratana.task_management_api.model.Notification;
import com.ratana.task_management_api.model.UserNotificationChannel;

public interface NotificationSender {
    NotificationChannel getChannel();
    void send(Notification notification);
}
