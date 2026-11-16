package com.ratana.task_management_api.notification.repository;


import com.ratana.task_management_api.notification.enums.NotificationChannel;
import com.ratana.task_management_api.notification.entity.UserNotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserNotificationChannelRepository extends JpaRepository<UserNotificationChannel, Integer> {

    List<UserNotificationChannel> findByUserId(UUID userId);

    List<UserNotificationChannel>findByUserIdAndEnabledTrue(UUID userId);
    Optional<UserNotificationChannel> findByUserIdAndChannel(UUID userId, NotificationChannel channel);
    boolean existsByUserIdAndChannelAndEnabledTrue(UUID userId, NotificationChannel channel);
}

