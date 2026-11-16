package com.ratana.task_management_api.notification.service;

import com.ratana.task_management_api.notification.dto.request.NotificationRequest;
import com.ratana.task_management_api.notification.dto.response.NotificationResponse;
import com.ratana.task_management_api.notification.enums.NotificationChannel;
import com.ratana.task_management_api.task.event.TaskEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface NotificationService {
    NotificationResponse send(NotificationRequest request);

    /** Decides who to notify about a task event and what to tell them; may decide to notify nobody. */
    void handleTaskEvent(TaskEvent event);

    List<NotificationChannel> resolveEnabledChannels(UUID userId);
    Page<NotificationResponse> getMyNotifications(UUID currentUserId, Pageable pageable);
    long getUnreadCount(UUID currentUserId);
    /** Idempotent: an already-read notification is left as is. 404 if it isn't the caller's. */
    void markAsRead(UUID notificationId, UUID currentUserId);

    /** Idempotent: an already-unread notification is left as is. 404 if it isn't the caller's. */
    void markAsUnread(UUID notificationId, UUID currentUserId);

    /** Returns how many notifications changed from unread to read. */
    int markAllAsRead(UUID currentUserId);

    /** Removes one of the caller's notifications (and its delivery records). 404 if it isn't theirs. */
    void delete(UUID notificationId, UUID currentUserId);



}
