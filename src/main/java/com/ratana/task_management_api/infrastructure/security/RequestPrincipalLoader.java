package com.ratana.task_management_api.infrastructure.security;

import com.ratana.task_management_api.common.cache.AppCaches;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * The user behind an access token, for {@link JwtAuthenticationFilter}. Cache-aside on
 * {@link AppCaches#principals()}, so most requests skip the user/roles/permissions query.
 *
 * <p>Login does not use this: it goes through {@link UserDetailsService} directly, so a changed
 * password is checked against the database straight away. Writes that change what this returns
 * (user status, roles, department, email; role and permission edits) invalidate the cache after
 * they commit; the TTL (app.cache.principal-ttl) covers changes made on another instance.
 *
 * <p>{@link UserPrincipal} is immutable, so one instance can be shared between requests.
 * A missing user throws and is not cached.
 */
@Component
@RequiredArgsConstructor
public class RequestPrincipalLoader {

    private final UserDetailsService userDetailsService;
    private final AppCaches caches;

    public UserDetails load(String email) {
        return caches.principals().get(email, () -> userDetailsService.loadUserByUsername(email));
    }
}
