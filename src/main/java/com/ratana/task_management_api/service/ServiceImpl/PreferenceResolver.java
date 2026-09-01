package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.enums.Channel;
import com.ratana.task_management_api.enums.NotificationType;
import com.ratana.task_management_api.model.UserNotificationChannel;
import com.ratana.task_management_api.repository.UserNotificationChannelRepository;
import jakarta.persistence.Cacheable;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
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
    }
}
