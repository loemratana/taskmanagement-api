package com.ratana.task_management_api.service;

import com.ratana.task_management_api.dto.NotificationRequest;
import com.ratana.task_management_api.dto.response.NotificationResponse;
import com.ratana.task_management_api.enums.NotificationChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface NotificationService {
    NotificationResponse send(NotificationRequest request);
    List<NotificationChannel> resolveEnabledChannels(UUID userId);
    Page<NotificationResponse> getMyNotifications(UUID currentUserId, Pageable pageable);
    long getUnreadCount(UUID currentUserId);
    void markAsRead(UUID notificationId, UUID currentUserId);



}
