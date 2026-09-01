package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.enums.DeliveryStatus;
import com.ratana.task_management_api.enums.NotificationChannel;
import com.ratana.task_management_api.enums.NotificationStatus;
import com.ratana.task_management_api.model.Notification;
import com.ratana.task_management_api.model.NotificationDelivery;
import com.ratana.task_management_api.repository.NotificationDeliveryRepository;
import com.ratana.task_management_api.repository.NotificationRepository;
import com.ratana.task_management_api.service.NotificationSender;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Single channel fan-out path. Retry scheduler reuses {@link #retry(NotificationDelivery)}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final List<NotificationSender> senders;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationRepository notificationRepository;

    @Value("${app.notification.retry.max-attempts:5}")
    private int maxAttempts;

    private Map<NotificationChannel, NotificationSender> senderMap = new EnumMap<>(NotificationChannel.class);

    @PostConstruct
    void init() {
        Map<NotificationChannel, NotificationSender> map = new EnumMap<>(NotificationChannel.class);
        for (NotificationSender sender : senders) {
            NotificationChannel channel = sender.getChannel();
            if (channel == null) {
                log.warn("Skipping NotificationSender {} with null channel", sender.getClass().getSimpleName());
                continue;
            }
            if (map.containsKey(channel)) {
                log.warn("Duplicate NotificationSender for channel {} — keeping {}",
                        channel, map.get(channel).getClass().getSimpleName());
                continue;
            }
            map.put(channel, sender);
        }
        senderMap = Map.copyOf(map);
        log.info("Registered notification senders: {}", senderMap.keySet());
    }

    @Async("notificationExecutor")
    @Transactional
    public void dispatch(Notification notification) {
        List<NotificationDelivery> deliveries = deliveryRepository.findByNotificationId(notification.getId());
        boolean anySuccess = false;

        for (NotificationDelivery delivery : deliveries) {
            if (attemptSend(delivery, notification)) {
                anySuccess = true;
            }
        }

        notification.setStatus(anySuccess ? NotificationStatus.SENT : NotificationStatus.FAILED);
        notificationRepository.save(notification);
    }

    /**
     * Retries a single failed delivery. Returns true if send succeeded.
     */
    @Transactional
    public boolean retry(NotificationDelivery delivery) {
        if (delivery.getStatus() == DeliveryStatus.DEAD_LETTER
                || delivery.getStatus() == DeliveryStatus.COMPLETED) {
            return delivery.getStatus() == DeliveryStatus.COMPLETED;
        }
        if (delivery.getAttempts() >= maxAttempts) {
            markDeadLetter(delivery, "Max retry attempts exceeded (" + maxAttempts + ")");
            return false;
        }

        Notification notification = delivery.getNotification();
        if (notification == null) {
            markFailed(delivery, "Missing notification on delivery");
            return false;
        }
        return attemptSend(delivery, notification);
    }

    private boolean attemptSend(NotificationDelivery delivery, Notification notification) {
        NotificationSender sender = senderMap.get(delivery.getChannel());
        delivery.incrementAttempts();

        if (sender == null) {
            log.warn("No sender registered for channel {}", delivery.getChannel());
            markFailed(delivery, "No sender registered for channel " + delivery.getChannel());
            return false;
        }

        try {
            sender.send(notification);
            markSent(delivery);
            return true;
        } catch (Exception e) {
            log.error("Failed to send notification {} via {}", notification.getId(), delivery.getChannel(), e);
            if (delivery.getAttempts() >= maxAttempts) {
                markDeadLetter(delivery, e.getMessage());
            } else {
                markFailed(delivery, e.getMessage());
            }
            return false;
        }
    }

    private void markFailed(NotificationDelivery delivery, String error) {
        delivery.setStatus(DeliveryStatus.FAILED);
        delivery.setErrorMessage(truncate(error));
        delivery.setLastAttemptAt(Instant.now());
        deliveryRepository.save(delivery);
    }

    private void markDeadLetter(NotificationDelivery delivery, String error) {
        delivery.setStatus(DeliveryStatus.DEAD_LETTER);
        delivery.setErrorMessage(truncate(error));
        delivery.setLastAttemptAt(Instant.now());
        delivery.setProcessedAt(Instant.now());
        deliveryRepository.save(delivery);
        log.warn("Delivery {} moved to DEAD_LETTER after {} attempts", delivery.getId(), delivery.getAttempts());
    }

    private void markSent(NotificationDelivery delivery) {
        delivery.setStatus(DeliveryStatus.COMPLETED);
        delivery.setErrorMessage(null);
        delivery.setSentAt(Instant.now());
        delivery.setProcessedAt(Instant.now());
        delivery.setLastAttemptAt(Instant.now());
        deliveryRepository.save(delivery);
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 1000 ? error.substring(0, 1000) : error;
    }
}
