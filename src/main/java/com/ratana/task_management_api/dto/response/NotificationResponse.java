package com.ratana.task_management_api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ratana.task_management_api.enums.NotificationChannel;
import com.ratana.task_management_api.enums.NotificationPriority;
import com.ratana.task_management_api.enums.NotificationStatus;
import com.ratana.task_management_api.enums.NotificationType;
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
    private String senderName;
    private String senderAvatarUrl;
    private String title;
    private String message;
    private NotificationType type;
    private NotificationPriority priority;
    private NotificationStatus status;
    @JsonProperty("isRead")
    private boolean isRead;
    private Instant readAt;
    private String referenceType;
    private UUID referenceId;
    private Map<String, Object> metadata;
    private Instant createdAt;
}
