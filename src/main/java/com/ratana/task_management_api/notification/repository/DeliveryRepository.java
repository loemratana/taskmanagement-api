package com.ratana.task_management_api.notification.repository;

import com.ratana.task_management_api.notification.entity.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {
}
