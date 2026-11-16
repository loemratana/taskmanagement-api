package com.ratana.task_management_api.common.config;

import com.ratana.task_management_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * V7 seeds a bootstrap admin with a password documented in the migration itself. Anyone with the
 * repository can sign in as admin until it is changed, so shout about it on every start.
 * Compares the stored BCrypt hash with the seeded one; the plaintext is never needed here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdminCheck {

    static final String BOOTSTRAP_ADMIN_EMAIL = "admin@example.com";
    /** password_hash inserted by V7__seed_roles_and_admin.sql. */
    static final String SEEDED_PASSWORD_HASH = "$2a$10$aRv7Yl/EejvRy8HQDRVQte6qilGYQ41FGEEHPzpQoIjBdEF2qOuFK";

    private final UserRepository userRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void warnIfSeededPasswordUnchanged() {
        userRepository.findByEmail(BOOTSTRAP_ADMIN_EMAIL)
                .filter(admin -> SEEDED_PASSWORD_HASH.equals(admin.getPasswordHash()))
                .filter(admin -> admin.isAccountEnabled())
                .ifPresent(admin -> log.error(
                        "SECURITY: bootstrap admin {} still has the default password from V7. "
                                + "Change it (PUT /api/v1/users/me/password) or deactivate the account.",
                        BOOTSTRAP_ADMIN_EMAIL));
    }
}
