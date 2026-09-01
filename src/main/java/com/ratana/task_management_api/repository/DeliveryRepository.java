package com.ratana.task_management_api.repository;

import com.ratana.task_management_api.model.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {
}
