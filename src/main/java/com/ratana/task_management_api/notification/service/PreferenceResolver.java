package com.ratana.task_management_api.notification.service;

import com.ratana.task_management_api.notification.repository.UserNotificationChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class PreferenceResolver {

    private final UserNotificationChannelRepository channelRepository;


//    @Cacheable(
//            value = "notificationPreferences",
//            key = "#userId"
//    )
//    public List<Channel> resolveChannels(UUID userId) {
//        return channelRepository.findEnabledChannels(userId);
//    }

    @CacheEvict(
            value = "notificationPreferences",
            key = "#userId"
    )
    public void clearCache(UUID userId) {
        // Cache is automatically evicted
        log.debug("Notification preference cache evicted for user {}", userId);
    }
}
