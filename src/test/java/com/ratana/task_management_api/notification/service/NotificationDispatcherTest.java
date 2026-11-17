package com.ratana.task_management_api.notification.service;

import com.ratana.task_management_api.notification.entity.Notification;
import com.ratana.task_management_api.notification.entity.NotificationDelivery;
import com.ratana.task_management_api.notification.enums.DeliveryStatus;
import com.ratana.task_management_api.notification.enums.NotificationChannel;
import com.ratana.task_management_api.notification.enums.NotificationStatus;
import com.ratana.task_management_api.notification.repository.NotificationDeliveryRepository;
import com.ratana.task_management_api.notification.repository.NotificationRepository;
import com.ratana.task_management_api.notification.sender.NotificationSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sends happen with no transaction; status is written with a targeted update, never save(). */
class NotificationDispatcherTest {

    private final NotificationSender telegram = mock(NotificationSender.class);
    private final NotificationDeliveryRepository deliveryRepository = mock(NotificationDeliveryRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private NotificationDispatcher dispatcher;

    private Notification notification;

    @BeforeEach
    void setUp() {
        when(telegram.getChannel()).thenReturn(NotificationChannel.TELEGRAM_GROUP);
        dispatcher = new NotificationDispatcher(List.of(telegram), deliveryRepository, notificationRepository);
        ReflectionTestUtils.setField(dispatcher, "maxAttempts", 6);
        dispatcher.init();
        notification = Notification.builder().id(UUID.randomUUID()).status(NotificationStatus.PENDING).build();
    }

    private NotificationDelivery delivery(DeliveryStatus status) {
        return NotificationDelivery.builder().id(UUID.randomUUID()).notification(notification)
                .channel(NotificationChannel.TELEGRAM_GROUP).status(status).attempts(0).build();
    }

    @Test
    @DisplayName("dispatch sends, marks the delivery completed and sets SENT via a targeted update")
    void dispatch_success() {
        NotificationDelivery delivery = delivery(DeliveryStatus.PENDING);
        when(deliveryRepository.findByNotificationId(notification.getId())).thenReturn(List.of(delivery));

        dispatcher.dispatch(notification);

        verify(telegram).send(notification);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.COMPLETED);
        verify(deliveryRepository).save(delivery);
        verify(notificationRepository).updateStatus(eq(notification.getId()), eq(NotificationStatus.SENT), any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("dispatch records a failed send and sets FAILED")
    void dispatch_failure() {
        NotificationDelivery delivery = delivery(DeliveryStatus.PENDING);
        when(deliveryRepository.findByNotificationId(notification.getId())).thenReturn(List.of(delivery));
        doThrow(new IllegalStateException("Telegram down")).when(telegram).send(notification);

        dispatcher.dispatch(notification);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getAttempts()).isEqualTo(1);
        verify(notificationRepository).updateStatus(eq(notification.getId()), eq(NotificationStatus.FAILED), any());
    }

    @Test
    @DisplayName("retry loads the delivery with its notification and upgrades the status to SENT")
    void retry_success() {
        notification.setStatus(NotificationStatus.FAILED);
        NotificationDelivery delivery = delivery(DeliveryStatus.FAILED);
        when(deliveryRepository.findWithNotificationById(delivery.getId())).thenReturn(Optional.of(delivery));

        assertThat(dispatcher.retryById(delivery.getId())).isTrue();

        verify(telegram).send(notification);
        verify(notificationRepository).updateStatus(eq(notification.getId()), eq(NotificationStatus.SENT), any());
        verify(notificationRepository, never()).save(any());
    }
}
