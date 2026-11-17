package com.ratana.task_management_api.notification.service;

import com.ratana.task_management_api.notification.enums.DeliveryStatus;
import com.ratana.task_management_api.notification.enums.NotificationChannel;
import com.ratana.task_management_api.notification.enums.NotificationStatus;
import com.ratana.task_management_api.notification.entity.Notification;
import com.ratana.task_management_api.notification.entity.NotificationDelivery;
import com.ratana.task_management_api.notification.repository.NotificationDeliveryRepository;
import com.ratana.task_management_api.notification.repository.NotificationRepository;
import com.ratana.task_management_api.notification.sender.NotificationSender;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single channel fan-out path. The retry scheduler reuses {@link #retryById(UUID)}.
 * <p>
 * No transaction wraps a send. Email, Telegram and FCM calls can take seconds, and a transaction
 * open around them would hold a pooled DB connection the whole time, so a slow provider could
 * starve API requests of connections. Instead every read and every status write is its own short
 * repository call, and the network call happens between them with no connection held.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final List<NotificationSender> senders;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationRepository notificationRepository;

    /** Total tries per delivery, the first send included (default: 1 + 5 back-off retries). */
    @Value("${app.notification.retry.max-attempts:6}")
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
    public void dispatch(Notification notification) {
        List<NotificationDelivery> deliveries = deliveryRepository.findByNotificationId(notification.getId());
        log.debug("Dispatching notification {} over {} delivery(ies)", notification.getId(), deliveries.size());
        boolean anySuccess = false;

        for (NotificationDelivery delivery : deliveries) {
            // Only first sends here. Anything else was already handled (e.g. the retry job recovered it).
            if (delivery.getStatus() != DeliveryStatus.PENDING) {
                anySuccess |= delivery.getStatus() == DeliveryStatus.COMPLETED;
                continue;
            }
            if (attemptSend(delivery, notification)) {
                anySuccess = true;
            }
        }

        NotificationStatus status = anySuccess ? NotificationStatus.SENT : NotificationStatus.FAILED;
        updateStatus(notification, status);
        log.info("Notification {} dispatched: status={}", notification.getId(), status);
    }

    /**
     * Retries one failed delivery, or sends one whose first dispatch was lost (still PENDING). Re-reads
     * it fresh, so it never acts on a stale copy, and like {@link #dispatch} holds no transaction while
     * sending. Returns true if the send succeeded.
     */
    public boolean retryById(UUID deliveryId) {
        NotificationDelivery delivery = deliveryRepository.findWithNotificationById(deliveryId).orElse(null);
        if (delivery == null) {
            return false;
        }
        log.debug("Retrying delivery {} (channel={}, status={}, attempts={})",
                delivery.getId(), delivery.getChannel(), delivery.getStatus(), delivery.getAttempts());
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
            log.warn("Delivery {} has no notification, marking failed", delivery.getId());
            markFailed(delivery, "Missing notification on delivery");
            return false;
        }
        boolean sent = attemptSend(delivery, notification);
        // Keep the notification's overall status in step: delivered once any channel got through.
        if (sent && notification.getStatus() != NotificationStatus.SENT) {
            updateStatus(notification, NotificationStatus.SENT);
        } else if (!sent && notification.getStatus() == NotificationStatus.PENDING) {
            updateStatus(notification, NotificationStatus.FAILED);
        }
        return sent;
    }

    /** Targeted update, not save(): merging this detached copy could undo a read the user made meanwhile. */
    private void updateStatus(Notification notification, NotificationStatus status) {
        notificationRepository.updateStatus(notification.getId(), status, Instant.now());
        notification.setStatus(status);
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
            sender.send(notification); // network call: no transaction or connection held here
            markSent(delivery);
            log.debug("Notification {} delivered via {} (attempt {})",
                    notification.getId(), delivery.getChannel(), delivery.getAttempts());
            return true;
        } catch (Exception e) {
            log.error("Failed to send notification | id={} channel={} type={} referenceId={} recipientId={} attempt={} error={}",
                    notification.getId(), delivery.getChannel(), notification.getType(),
                    notification.getReferenceId(), notification.getRecipientId(), delivery.getAttempts(),
                    e.getMessage(), e);
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
