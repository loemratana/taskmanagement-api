package com.ratana.task_management_api.websocket;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Ensures users can only SUBSCRIBE to their own notification destinations.
 */
@Slf4j
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String USER_QUEUE = "/user/queue/notifications";
    private static final String TOPIC_PREFIX = "/topic/notifications/";

    @Override
    public @Nullable Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (!StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        String authenticatedUserId = resolveUserId(accessor);

        if (destination.equals(USER_QUEUE)
                || destination.startsWith("/user/" + authenticatedUserId + "/queue/notifications")) {
            return message;
        }

        if (destination.startsWith(TOPIC_PREFIX)) {
            String requestedUserId = destination.substring(TOPIC_PREFIX.length());
            if (!requestedUserId.equals(authenticatedUserId)) {
                log.warn("Rejected subscription to {} by user {}", destination, authenticatedUserId);
                throw new org.springframework.security.access.AccessDeniedException(
                        "Cannot subscribe to another user's notification channel.");
            }
            return message;
        }

        // Block unknown notification destinations
        if (destination.contains("notifications")) {
            log.warn("Rejected subscription to unexpected destination {} by user {}",
                    destination, authenticatedUserId);
            throw new org.springframework.security.access.AccessDeniedException(
                    "Subscription destination not allowed.");
        }

        return message;
    }

    private String resolveUserId(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof AbstractAuthenticationToken auth
                && auth.getPrincipal() instanceof StompUserPrincipal principal) {
            return principal.id().toString();
        }
        if (accessor.getUser() != null) {
            return accessor.getUser().getName();
        }
        throw new org.springframework.security.access.AccessDeniedException(
                "No authenticated principal on WebSocket session.");
    }
}
