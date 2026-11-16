package com.ratana.task_management_api.notification.service;

import com.ratana.task_management_api.notification.enums.NotificationChannel;
import com.ratana.task_management_api.notification.enums.NotificationType;
import com.ratana.task_management_api.notification.entity.UserNotificationChannel;
import com.ratana.task_management_api.notification.repository.UserNotificationChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves per-user channel preferences for notification dispatch.
 * IN_APP is always enabled (inbox). Other channels require an enabled preference row.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {

    private static final Set<NotificationChannel> DEFAULT_CHANNELS =
            EnumSet.of(NotificationChannel.IN_APP, NotificationChannel.PUSH);

    private static final Set<NotificationType> FORCE_EMAIL_TYPES = EnumSet.of(
            NotificationType.SYSTEM_ANNOUNCEMENT,
            NotificationType.SYSTEM_MAINTENANCE,
            NotificationType.APPROVAL_REQUEST
    );

    private final UserNotificationChannelRepository channelRepository;
    private final ObjectMapper objectMapper;


    @Override
    @Transactional(readOnly = true)
    public Set<NotificationChannel> resolveEnabledChannels(UUID userId, NotificationType type) {
        List<UserNotificationChannel> prefs = channelRepository.findByUserId(userId);

        Set<NotificationChannel> enabled = new LinkedHashSet<>();
        enabled.add(NotificationChannel.IN_APP);

        if (prefs.isEmpty()) {
            enabled.addAll(DEFAULT_CHANNELS);
        } else {
            prefs.stream()
                    .filter(UserNotificationChannel::isEnabled)
                    .map(UserNotificationChannel::getChannel)
                    .forEach(enabled::add);
        }

        if (type != null && FORCE_EMAIL_TYPES.contains(type)) {
            enabled.add(NotificationChannel.EMAIL);
        }

        log.debug("Resolved channels for user {} type {}: {} (defaults={})", userId, type, enabled, prefs.isEmpty());
        return enabled;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> resolveChannelConfig(UUID userId, NotificationChannel channel) {
        return channelRepository.findByUserIdAndChannel(userId, channel)
                .map(UserNotificationChannel::getConfig)
                .filter(StringUtils::hasText)
                .map(this::parseConfig)
                .orElseGet(Map::of);
    }

    @Override
    @Transactional
    public void updateChannelPreference(UUID userId, NotificationChannel channel, boolean enabled,
                                        Map<String, Object> config) {
        String configJson = null;
        if (config != null && !config.isEmpty()) {
            try {
                configJson = objectMapper.writeValueAsString(config);
            } catch (Exception e) {
                log.warn("Invalid {} channel config for user {}: {}", channel, userId, e.getMessage());
                throw new IllegalArgumentException("Invalid channel config JSON", e);
            }
        }

        final String finalConfig = configJson;
        channelRepository.findByUserIdAndChannel(userId, channel)
                .ifPresentOrElse(pref -> {
                    pref.setEnabled(enabled);
                    if (finalConfig != null) {
                        pref.setConfig(finalConfig);
                    }
                    channelRepository.save(pref);
                }, () -> channelRepository.save(UserNotificationChannel.builder()
                        .userId(userId)
                        .channel(channel)
                        .enabled(enabled)
                        .config(finalConfig)
                        .build()));
        log.info("Notification channel {} {} for user {}", channel, enabled ? "enabled" : "disabled", userId);
    }

    @Transactional(readOnly = true)
    public List<UserNotificationChannel> getPreferences(UUID userId) {
        log.debug("Fetching notification preferences for user {}", userId);
        return channelRepository.findByUserId(userId);
    }

    private Map<String, Object> parseConfig(String json) {
        try {
            return objectMapper.readValue(json, new  TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse notification channel config: {}", e.getMessage());
            return Map.of();
        }
    }
}
