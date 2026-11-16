package com.ratana.task_management_api.notification.scheduler;

import com.ratana.task_management_api.notification.entity.NotificationDelivery;
import com.ratana.task_management_api.notification.enums.DeliveryStatus;
import com.ratana.task_management_api.notification.repository.NotificationDeliveryRepository;
import com.ratana.task_management_api.notification.service.DeliveryRetryPolicy;
import com.ratana.task_management_api.notification.service.NotificationDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrySchedulerTest {

    private static final Instant NOW = Instant.parse("2026-11-16T09:00:00Z");

    private final NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
    private final NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
    private final DeliveryRetryPolicy policy = new DeliveryRetryPolicy("1,2,5,15,30", 10, 100);
    private final RetryScheduler scheduler =
            new RetryScheduler(repository, dispatcher, policy, Clock.fixed(NOW, ZoneOffset.UTC));

    private static NotificationDelivery failed(int attempts, Duration since) {
        return NotificationDelivery.builder().id(UUID.randomUUID()).status(DeliveryStatus.FAILED)
                .attempts(attempts).lastAttemptAt(NOW.minus(since)).build();
    }

    @Test
    @DisplayName("Back-off grows per attempt and the last step repeats")
    void policy_backoff() {
        assertThat(policy.delayAfter(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.delayAfter(3)).isEqualTo(Duration.ofMinutes(5));
        assertThat(policy.delayAfter(9)).isEqualTo(Duration.ofMinutes(30));
        assertThat(policy.isDue(failed(2, Duration.ofMinutes(2)), NOW)).isTrue();
        assertThat(policy.isDue(failed(4, Duration.ofMinutes(10)), NOW)).isFalse();   // needs 15
    }

    @Test
    @DisplayName("Resends lost (stuck PENDING) deliveries and retries only the failed ones that are due")
    void retries_stuckAndDue() {
        UUID stuck = UUID.randomUUID();
        NotificationDelivery due = failed(1, Duration.ofMinutes(3));
        NotificationDelivery notYet = failed(3, Duration.ofMinutes(3));   // waits 5 minutes
        when(repository.findStuckIds(eq(DeliveryStatus.PENDING), eq(NOW.minus(Duration.ofMinutes(10))), any()))
                .thenReturn(List.of(stuck));
        when(repository.findRetryCandidates(eq(DeliveryStatus.FAILED), any(), any())).thenReturn(List.of(due, notYet));
        when(dispatcher.retryById(any())).thenReturn(true);

        scheduler.retryFailedDeliveries();

        verify(dispatcher).retryById(stuck);
        verify(dispatcher).retryById(due.getId());
        verify(dispatcher, never()).retryById(notYet.getId());
    }

    @Test
    @DisplayName("One failing retry doesn't stop the rest")
    void oneFailureDoesNotStopTheBatch() {
        NotificationDelivery first = failed(1, Duration.ofMinutes(5));
        NotificationDelivery second = failed(1, Duration.ofMinutes(5));
        when(repository.findStuckIds(any(), any(), any())).thenReturn(List.of());
        when(repository.findRetryCandidates(any(), any(), any())).thenReturn(List.of(first, second));
        when(dispatcher.retryById(first.getId())).thenThrow(new IllegalStateException("FCM down"));

        scheduler.retryFailedDeliveries();

        verify(dispatcher).retryById(second.getId());
    }
}
