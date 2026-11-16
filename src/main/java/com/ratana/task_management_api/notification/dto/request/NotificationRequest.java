package com.ratana.task_management_api.notification.dto.request;

import com.ratana.task_management_api.notification.enums.NotificationChannel;
import com.ratana.task_management_api.notification.enums.NotificationPriority;
import com.ratana.task_management_api.notification.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRequest {

    @NotNull
    private UUID recipientId;

    @NotNull
    private UUID senderId;

    @NotBlank
    private String title;

    @NotBlank
    private String message;

    @NotNull
    private NotificationType type;

    @Builder.Default
    private NotificationPriority priority = NotificationPriority.MEDIUM;

    private String referenceType;
    private UUID referenceId;

    private Map<String, Object> metadata;

    /**
     * Channels to include regardless of the recipient's personal preferences — for
     * org-mandated broadcasts (e.g. TELEGRAM_GROUP) rather than opt-in personal channels.
     * The caller decides whether the condition for forcing a channel is met.
     */
    private Set<NotificationChannel> forcedChannels;

    /**
     * When set, delivery is limited to these channels (applied after preferences and
     * forcedChannels) — e.g. an announcement the author chose to send in-app only.
     * Null means no limit.
     */
    private Set<NotificationChannel> allowedChannels;
}
