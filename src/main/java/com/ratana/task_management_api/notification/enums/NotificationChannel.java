package com.ratana.task_management_api.notification.enums;

public enum NotificationChannel {
    IN_APP,
    EMAIL,
    /** Personal DM to a user, via UserNotificationChannel.config's telegram_chat_id. */
    TELEGRAM,
    /** Department broadcast, via DepartmentTelegramGroup — not user-preference-gated. */
    TELEGRAM_GROUP,
    PUSH
}
