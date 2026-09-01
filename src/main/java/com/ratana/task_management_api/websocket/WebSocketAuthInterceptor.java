package com.ratana.task_management_api.websocket;

import com.ratana.task_management_api.security.JwtTokenProvider;
import com.ratana.task_management_api.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Authenticates STOMP CONNECT with JWT (Authorization header or native token header).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    public @Nullable Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String token = resolveToken(accessor);
        if (!StringUtils.hasText(token)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "WebSocket CONNECT requires a JWT token.");
        }

        String email = jwtTokenProvider.extractUsername(token);
        UserPrincipal userPrincipal = (UserPrincipal) userDetailsService.loadUserByUsername(email);
        if (!jwtTokenProvider.validateToken(token, userPrincipal)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid or expired JWT.");
        }

        StompUserPrincipal stompPrincipal = new StompUserPrincipal(
                userPrincipal.getId(),
                userPrincipal.getEmail(),
                userPrincipal.getAuthorities()
        );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(stompPrincipal, null, userPrincipal.getAuthorities());
        accessor.setUser(authentication);
        log.debug("WebSocket CONNECT authenticated for user {}", userPrincipal.getId());
        return message;
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        String tokenHeader = accessor.getFirstNativeHeader("token");
        if (StringUtils.hasText(tokenHeader)) {
            return tokenHeader.startsWith(BEARER_PREFIX)
                    ? tokenHeader.substring(BEARER_PREFIX.length())
                    : tokenHeader;
        }
        return null;
    }
}
