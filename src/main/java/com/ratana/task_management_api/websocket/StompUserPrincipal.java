package com.ratana.task_management_api.websocket;

import java.security.Principal;
import java.util.Collection;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;

/**
 * STOMP principal whose {@link #getName()} is the user id string so
 * {@code convertAndSendToUser(userId, ...)} resolves correctly.
 */
public record StompUserPrincipal(
        UUID id,
        String email,
        Collection<? extends GrantedAuthority> authorities
) implements Principal {

    @Override
    public String getName() {
        return id.toString();
    }
}
