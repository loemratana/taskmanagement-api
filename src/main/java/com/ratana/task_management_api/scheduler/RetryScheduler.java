package com.ratana.task_management_api.scheduler;

import com.ratana.task_management_api.enums.DeliveryStatus;
import com.ratana.task_management_api.model.NotificationDelivery;
import com.ratana.task_management_api.repository.NotificationDeliveryRepository;
import com.ratana.task_management_api.service.ServiceImpl.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Retries failed notification deliveries through the same {@link NotificationDispatcher} path.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RetryScheduler {

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationDispatcher dispatcher;


    /**
     * Maximum retry attempts allowed before moving notification
     * into DEAD_LETTER state.
     *
     * Example:
     * attempts = 5
     * maxAttempts = 5
     *
     * Stop retrying.
     */
    @Value("${app.notification.retry.max-attempts:5}")
    private int maxAttempts;



    /**
     * Background job that retries failed notification deliveries.
     *
     * Flow:
     *
     * 1. Scheduler runs periodically.
     * 2. Find notifications with FAILED status.
     * 3. Check retry attempts.
     * 4. Retry sending through NotificationDispatcher.
     * 5. If successful -> DELIVERED.
     * 6. If failed too many times -> DEAD_LETTER.
     */
    @Scheduled(fixedDelayString = "${app.notification.retry.interval-ms:60000}")
    @Transactional
    public void retryFailedDeliveries() {
        List<NotificationDelivery> failed = deliveryRepository.findByStatus(DeliveryStatus.FAILED);
        if (failed.isEmpty()) {
            return;
        }

        log.info("Retrying {} failed notification deliveries", failed.size());
        int success = 0;
        int stillFailed = 0;

        for (NotificationDelivery delivery : failed) {
            if (delivery.getAttempts() >= maxAttempts) {
                delivery.setStatus(DeliveryStatus.DEAD_LETTER);
                deliveryRepository.save(delivery);
                stillFailed++;
                continue;
            }

            try {
                if (dispatcher.retry(delivery)) {
                    success++;
                } else {
                    stillFailed++;
                }
            } catch (Exception e) {
                stillFailed++;
                log.warn("Retry failed for delivery {}: {}", delivery.getId(), e.getMessage());
            }
        }

        log.info("Notification retry finished: success={}, failedOrDead={}", success, stillFailed);
    }
}
