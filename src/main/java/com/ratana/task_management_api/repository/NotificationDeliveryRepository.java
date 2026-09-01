package com.ratana.task_management_api.repository;

import com.ratana.task_management_api.enums.DeliveryStatus;
import com.ratana.task_management_api.enums.NotificationChannel;
import com.ratana.task_management_api.model.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

    List<NotificationDelivery> findByNotificationId(UUID notificationId);
    List<NotificationDelivery> findByStatus(DeliveryStatus status);

    Optional<NotificationDelivery> findByNotificationIdAndChannel(UUID notificationId, NotificationChannel channel);
}
