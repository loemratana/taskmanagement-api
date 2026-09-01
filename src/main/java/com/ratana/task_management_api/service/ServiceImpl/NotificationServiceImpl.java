package com.ratana.task_management_api.service.ServiceImpl;

import com.google.api.pathtemplate.ValidationException;
import com.ratana.task_management_api.dto.NotificationRequest;
import com.ratana.task_management_api.dto.response.NotificationResponse;
import com.ratana.task_management_api.enums.DeliveryStatus;
import com.ratana.task_management_api.enums.NotificationChannel;
import com.ratana.task_management_api.enums.NotificationStatus;
import com.ratana.task_management_api.enums.NotificationType;
import com.ratana.task_management_api.exception.NotificationException;
import com.ratana.task_management_api.model.Notification;
import com.ratana.task_management_api.model.NotificationDelivery;
import com.ratana.task_management_api.repository.NotificationDeliveryRepository;
import com.ratana.task_management_api.repository.NotificationRepository;
import com.ratana.task_management_api.service.NotificationPreferenceService;
import com.ratana.task_management_api.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationPreferenceService preferenceService;
    private final NotificationDispatcher dispatcher;

    @Override
    @Transactional
    public NotificationResponse send(NotificationRequest request) {
        if (request.getRecipientId() == null) {
            throw new ValidationException("Recipient ID cannot be null.");
        }
        if (request.getSenderId() == null) {
            throw new ValidationException("Sender ID cannot be null.");
        }

        log.info("Sending notification type={} recipient={} title={}",
                request.getType(), request.getRecipientId(), request.getTitle());

        Notification notification = Notification.builder()
                .recipientId(request.getRecipientId())
                .senderId(request.getSenderId())
                .title(request.getTitle())
                .message(request.getMessage())
                .type(request.getType())
                .priority(request.getPriority())
                .referenceType(request.getReferenceType())
                .referenceId(request.getReferenceId())
                .metadata(request.getMetadata() != null ? request.getMetadata() : new HashMap<>())
                .status(NotificationStatus.PENDING)
                .build();

        notification = notificationRepository.save(notification);

        List<NotificationChannel> enabledChannels = resolveEnabledChannels(
                request.getRecipientId(), request.getType());

        if (request.getForcedChannels() != null) {
            for (NotificationChannel channel : request.getForcedChannels()) {
                if (!enabledChannels.contains(channel)) {
                    enabledChannels.add(channel);
                }
            }
        }

        for (NotificationChannel channel : enabledChannels) {
            deliveryRepository.save(NotificationDelivery.builder()
                    .notification(notification)
                    .channel(channel)
                    .status(DeliveryStatus.PENDING)
                    .attempts(0)
                    .build());
        }

        // Dispatch only after this TX commits so async workers see persisted rows
        final UUID notificationId = notification.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationRepository.findById(notificationId)
                        .ifPresent(dispatcher::dispatch);
            }
        });

        return toResponse(notification);
    }

    @Override
    public List<NotificationChannel> resolveEnabledChannels(UUID userId) {
        return resolveEnabledChannels(userId, null);
    }

    private List<NotificationChannel> resolveEnabledChannels(UUID userId, NotificationType type) {
        return new ArrayList<>(preferenceService.resolveEnabledChannels(userId, type));
    }

    @Override
    public Page<NotificationResponse> getMyNotifications(UUID currentUserId, Pageable pageable) {
        return notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(currentUserId, pageable)
                .map(this::toResponse);
    }

    @Override
    public long getUnreadCount(UUID currentUserId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(currentUserId);
    }

    @Override
    public void markAsRead(UUID notificationId, UUID currentUserId) {
        int updated = notificationRepository.markAsRead(notificationId, currentUserId);
        if (updated == 0) {
            throw new NotificationException("Notification not found or already read");
        }
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .recipientId(n.getRecipientId())
                .senderId(n.getSenderId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .priority(n.getPriority())
                .status(n.getStatus())
                .isRead(n.isRead())
                .readAt(n.getReadAt())
                .referenceType(n.getReferenceType())
                .referenceId(n.getReferenceId())
                .metadata(n.getMetadata())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
