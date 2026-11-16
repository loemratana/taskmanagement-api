package com.ratana.task_management_api.notification.dto.response;


import com.ratana.task_management_api.notification.enums.NotificationPriority;
import com.ratana.task_management_api.notification.enums.NotificationType;
import jakarta.validation.Valid;
import lombok.Builder;

import java.util.Map;
import java.util.UUID;

@Valid
@Builder
public class NotificationCommand {
    UUID recipientId;
    UUID senderId;
    String title;
    String message;
    NotificationType type;
    NotificationPriority priority;
    String referenceType;
    UUID referenceId;
    Map<String, Object> metadata;
}
