package com.ratana.task_management_api.notification.service;

import com.ratana.task_management_api.notification.entity.NotificationDelivery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * When a failed delivery may be tried again, and when a never-sent one counts as stuck.
 * The wait grows with each attempt (1, 2, 5, 15, 30 minutes by default), so a provider outage
 * of up to about an hour is ridden out instead of exhausting every attempt within minutes.
 */
@Component
public class DeliveryRetryPolicy {

    private final List<Duration> backoff;
    private final Duration stuckAfter;
    private final int batchSize;

    public DeliveryRetryPolicy(
            @Value("${app.notification.retry.backoff-minutes:1,2,5,15,30}") String backoffMinutes,
            @Value("${app.notification.retry.stuck-after-minutes:10}") long stuckAfterMinutes,
            @Value("${app.notification.retry.batch-size:100}") int batchSize) {
        this.backoff = Arrays.stream(backoffMinutes.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(minutes -> Duration.ofMinutes(Long.parseLong(minutes)))
                .toList();
        if (backoff.isEmpty()) {
            throw new IllegalArgumentException("app.notification.retry.backoff-minutes needs at least one value");
        }
        this.stuckAfter = Duration.ofMinutes(stuckAfterMinutes);
        this.batchSize = batchSize;
    }

    /** The wait after the {@code attempts}-th attempt; the last value repeats for later attempts. */
    public Duration delayAfter(int attempts) {
        return backoff.get(Math.min(Math.max(attempts, 1), backoff.size()) - 1);
    }

    /** A failed delivery is due once its back-off since the last attempt has passed. */
    public boolean isDue(NotificationDelivery delivery, Instant now) {
        Instant last = delivery.getLastAttemptAt();
        return last == null || !last.plus(delayAfter(delivery.getAttempts())).isAfter(now);
    }

    /** The shortest wait: nothing tried more recently than this can be due. */
    public Duration shortestDelay() {
        return backoff.stream().min(Duration::compareTo).orElseThrow();
    }

    /**
     * A delivery still PENDING this long after its notification was created was queued in memory and
     * lost (restart, crash, full queue). Well above normal dispatch time, so it never races a live send.
     */
    public Duration stuckAfter() {
        return stuckAfter;
    }

    public int batchSize() {
        return batchSize;
    }
}
