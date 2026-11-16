package com.ratana.task_management_api.auth.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

/**
 * Brute-force protection for the public auth endpoints.
 * <ul>
 *   <li>Login: limited per email (protects one account) and per client IP (slows credential stuffing).
 *       Only failures count; a successful login clears that email's counter.</li>
 *   <li>Forgot password: every request counts, per email and per IP, so the endpoint can't be used
 *       to flood someone's inbox.</li>
 * </ul>
 */
@Slf4j
@Component
public class AuthRateLimiter {

    private final AttemptLimiter loginByEmail;
    private final AttemptLimiter loginByIp;
    private final AttemptLimiter resetByEmail;
    private final AttemptLimiter resetByIp;

    public AuthRateLimiter(
            @Value("${app.security.login.max-failures-per-email:5}") int loginMaxPerEmail,
            @Value("${app.security.login.max-failures-per-ip:20}") int loginMaxPerIp,
            @Value("${app.security.login.window-minutes:15}") long loginWindowMinutes,
            @Value("${app.security.password-reset.max-requests-per-email:3}") int resetMaxPerEmail,
            @Value("${app.security.password-reset.max-requests-per-ip:10}") int resetMaxPerIp,
            @Value("${app.security.password-reset.window-minutes:15}") long resetWindowMinutes) {
        Clock clock = Clock.systemUTC();
        Duration loginWindow = Duration.ofMinutes(loginWindowMinutes);
        Duration resetWindow = Duration.ofMinutes(resetWindowMinutes);
        this.loginByEmail = new AttemptLimiter("login", loginMaxPerEmail, loginWindow, clock);
        this.loginByIp = new AttemptLimiter("login", loginMaxPerIp, loginWindow, clock);
        this.resetByEmail = new AttemptLimiter("password-reset", resetMaxPerEmail, resetWindow, clock);
        this.resetByIp = new AttemptLimiter("password-reset", resetMaxPerIp, resetWindow, clock);
    }

    public void assertLoginAllowed(String email, String ip) {
        loginByEmail.assertAllowed(normalize(email));
        loginByIp.assertAllowed(ip);
    }

    public void loginFailed(String email, String ip) {
        loginByEmail.record(normalize(email));
        loginByIp.record(ip);
    }

    public void loginSucceeded(String email) {
        loginByEmail.reset(normalize(email));
    }

    /** Checks and counts one forgot-password request. */
    public void consumePasswordResetRequest(String email, String ip) {
        String key = normalize(email);
        resetByEmail.assertAllowed(key);
        resetByIp.assertAllowed(ip);
        resetByEmail.record(key);
        resetByIp.record(ip);
    }

    @Scheduled(fixedDelay = 600_000)
    void evictExpired() {
        loginByEmail.evictExpired();
        loginByIp.evictExpired();
        resetByEmail.evictExpired();
        resetByIp.evictExpired();
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
