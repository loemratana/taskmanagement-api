package com.ratana.task_management_api.websocket;

import com.ratana.task_management_api.dto.response.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publishes in-app notifications to the authenticated user's private queue.
 * Destination aligns with {@link com.ratana.task_management_api.service.sender.WebSocketNotificationSender}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketPublisher {

    private static final String USER_QUEUE = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(UUID recipientId, Object payload) {
        messagingTemplate.convertAndSendToUser(
                recipientId.toString(),
                USER_QUEUE,
                payload
        );
        log.debug("Published WebSocket notification to user {}", recipientId);
    }

    public void publish(UUID recipientId, NotificationResponse payload) {
        publish(recipientId, (Object) payload);
    }
}
