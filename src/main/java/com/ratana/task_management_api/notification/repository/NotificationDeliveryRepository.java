package com.ratana.task_management_api.notification.repository;

import com.ratana.task_management_api.notification.enums.DeliveryStatus;
import com.ratana.task_management_api.notification.enums.NotificationChannel;
import com.ratana.task_management_api.notification.entity.NotificationDelivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

    List<NotificationDelivery> findByNotificationId(UUID notificationId);
    List<NotificationDelivery> findByStatus(DeliveryStatus status);

    Optional<NotificationDelivery> findByNotificationIdAndChannel(UUID notificationId, NotificationChannel channel);

    /** Retry candidates: deliveries in {@code status} last tried before {@code triedBefore}, longest-waiting first. */
    @Query("SELECT d FROM NotificationDelivery d WHERE d.status = :status"
            + " AND (d.lastAttemptAt IS NULL OR d.lastAttemptAt <= :triedBefore) ORDER BY d.lastAttemptAt ASC")
    List<NotificationDelivery> findRetryCandidates(@Param("status") DeliveryStatus status,
                                                   @Param("triedBefore") Instant triedBefore, Pageable pageable);

    /**
     * Deliveries never sent although their notification was created before {@code createdBefore}: the
     * in-memory dispatch was lost (restart or crash). Delivery rows have no reliable created time of their
     * own, so the notification's is used.
     */
    @Query("SELECT d.id FROM NotificationDelivery d WHERE d.status = :status"
            + " AND d.notification.createdAt <= :createdBefore ORDER BY d.notification.createdAt ASC")
    List<UUID> findStuckIds(@Param("status") DeliveryStatus status,
                            @Param("createdBefore") Instant createdBefore, Pageable pageable);
}
