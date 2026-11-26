package com.ratana.task_management_api.notification.scheduler;

import com.ratana.task_management_api.notification.entity.NotificationDelivery;
import com.ratana.task_management_api.notification.enums.DeliveryStatus;
import com.ratana.task_management_api.notification.repository.NotificationDeliveryRepository;
import com.ratana.task_management_api.notification.service.DeliveryRetryPolicy;
import com.ratana.task_management_api.notification.service.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Makes notification delivery durable. The notification_deliveries table is the queue: every
 * delivery is saved before it's sent, and this job finishes whatever didn't get through.
 * <ul>
 *   <li><b>Stuck</b>: still PENDING long after creation — its in-memory dispatch was lost to a
 *       restart, crash or full queue. Sent now.</li>
 *   <li><b>Failed</b>: retried with a growing wait ({@link DeliveryRetryPolicy}), then dead-lettered.</li>
 * </ul>
 * Each retry is independent (NotificationDispatcher#retryById), so one bad delivery can't affect
 * the others, and no transaction is held open while it sends; a batch limit keeps a backlog from
 * monopolising the scheduler thread the reminders also use.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RetryScheduler {

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationDispatcher dispatcher;
    private final DeliveryRetryPolicy policy;
    private final Clock clock;

    @SchedulerLock(name = "notification-retry", lockAtLeastFor = "PT30S", lockAtMostFor = "PT10M")
    @Scheduled(fixedDelayString = "${app.notification.retry.interval-ms:60000}")
    public void retryFailedDeliveries() {
        Instant now = clock.instant();
        Pageable batch = PageRequest.of(0, policy.batchSize());

        List<UUID> stuck = deliveryRepository.findStuckIds(DeliveryStatus.PENDING, now.minus(policy.stuckAfter()), batch);
        List<UUID> due = deliveryRepository
                .findRetryCandidates(DeliveryStatus.FAILED, now.minus(policy.shortestDelay()), batch).stream()
                .filter(delivery -> policy.isDue(delivery, now))
                .map(NotificationDelivery::getId)
                .toList();
        if (stuck.isEmpty() && due.isEmpty()) {
            return;
        }
        if (!stuck.isEmpty()) {
            log.warn("Recovering {} notification delivery(ies) whose dispatch was lost (still PENDING)", stuck.size());
        }

        int sent = 0;
        int failed = 0;
        for (UUID deliveryId : concat(stuck, due)) {
            try {
                if (dispatcher.retryById(deliveryId)) {
                    sent++;
                } else {
                    failed++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("Retry of delivery {} failed: {}", deliveryId, e.getMessage());
            }
        }
        log.info("Notification retry run: {} recovered, {} retried -> {} sent, {} still failing or dead-lettered",
                stuck.size(), due.size(), sent, failed);
    }

    private static List<UUID> concat(List<UUID> first, List<UUID> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }
}
