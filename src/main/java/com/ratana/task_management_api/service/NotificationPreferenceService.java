package com.ratana.task_management_api.service;

import com.ratana.task_management_api.enums.NotificationChannel;
import com.ratana.task_management_api.enums.NotificationType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface NotificationPreferenceService {

    /**
     * IN_APP is always included - it's the inbox itself. EMAIL/TELEGRAM depend on the user's
     * UserNotificationChannel row for that channel (defaulting to disabled if no row exists -
     * TELEGRAM in particular requires the user to have linked an account, so "no row" genuinely
     * means "not usable yet", not just "no explicit preference set"). Security-critical types
     * force EMAIL on regardless.
     */
    Set<NotificationChannel> resolveEnabledChannels(UUID userId, NotificationType type);

    /** Parsed contents of UserNotificationChannel.config for this user+channel, or empty if none configured. */
    Map<String, Object> resolveChannelConfig(UUID userId, NotificationChannel channel);

    void updateChannelPreference(UUID userId, NotificationChannel channel, boolean enabled, Map<String, Object> config);
}
