package com.ratana.task_management_api.notification.dto.response;

import com.ratana.task_management_api.notification.enums.NotificationPriority;
import com.ratana.task_management_api.notification.enums.NotificationStatus;
import com.ratana.task_management_api.notification.enums.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;


@Getter
@Builder
public class NotificationResponse {

    private UUID id;
    private UUID recipientId;
    private UUID senderId;
    private String title;
    private String message;
    private NotificationType type;
    private NotificationPriority priority;
    private NotificationStatus status;
    private boolean isRead;
    private Instant readAt;
    private String referenceType;
    private UUID referenceId;
    private Map<String, Object> metadata;
    private Instant createdAt;
}
