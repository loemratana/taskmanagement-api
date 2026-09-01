package com.ratana.task_management_api.dto.response;


import com.ratana.task_management_api.enums.NotificationPriority;
import com.ratana.task_management_api.enums.NotificationType;
import jakarta.validation.Valid;
import lombok.Builder;
import org.springframework.beans.factory.annotation.Value;

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
