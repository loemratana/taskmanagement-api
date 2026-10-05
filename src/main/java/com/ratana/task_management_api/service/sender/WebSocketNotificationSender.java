package com.ratana.task_management_api.service.sender;

import com.ratana.task_management_api.dto.response.NotificationResponse;
import com.ratana.task_management_api.enums.NotificationChannel;
import com.ratana.task_management_api.model.Notification;
import com.ratana.task_management_api.model.User;
import com.ratana.task_management_api.model.UserNotificationChannel;
import com.ratana.task_management_api.repository.UserRepository;
import com.ratana.task_management_api.service.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketNotificationSender implements NotificationSender {
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.IN_APP;    }

    @Override
    public void send(Notification notification) {
        try {
            NotificationResponse payload = toResponse(notification);
            String destination = "/queue/notifications";

            messagingTemplate.convertAndSendToUser(
                    notification.getRecipientId().toString(),
                    destination,
                    payload
            );

            log.debug("WebSocket notification sent to user {}", notification.getRecipientId());
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification to user {}", notification.getRecipientId(), e);
            throw e;
        }

    }

    private NotificationResponse toResponse(Notification n) {
        User sender = n.getSenderId() != null
                ? userRepository.findById(n.getSenderId()).orElse(null)
                : null;
        return NotificationResponse.builder()
                .id(n.getId())
                .recipientId(n.getRecipientId())
                .senderId(n.getSenderId())
                .senderName(sender != null ? sender.getFullName() : null)
                .senderAvatarUrl(sender != null ? sender.getAvatarUrl() : null)
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
