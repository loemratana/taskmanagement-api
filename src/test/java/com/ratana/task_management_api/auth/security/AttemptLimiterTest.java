package com.ratana.task_management_api.auth.security;

import com.ratana.task_management_api.auth.exception.TooManyAttemptsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttemptLimiterTest {

    /** Clock the test can move forward. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final AttemptLimiter limiter = new AttemptLimiter("login", 3, Duration.ofMinutes(15), clock);

    @Test
    void blocksAfterMaxAttempts_withRetryAfter() {
        limiter.record("a@x.com");
        limiter.record("a@x.com");
        assertThatCode(() -> limiter.assertAllowed("a@x.com")).doesNotThrowAnyException();

        limiter.record("a@x.com");
        assertThatThrownBy(() -> limiter.assertAllowed("a@x.com"))
                .isInstanceOf(TooManyAttemptsException.class)
                .satisfies(ex -> assertThat(((TooManyAttemptsException) ex).getRetryAfterSeconds()).isEqualTo(900));
    }

    @Test
    void keysAreIndependent() {
        limiter.record("a@x.com");
        limiter.record("a@x.com");
        limiter.record("a@x.com");

        assertThatCode(() -> limiter.assertAllowed("b@x.com")).doesNotThrowAnyException();
    }

    @Test
    void unblocksOnceOldestAttemptLeavesWindow() {
        limiter.record("a@x.com");
        clock.advance(Duration.ofMinutes(5));
        limiter.record("a@x.com");
        limiter.record("a@x.com");
        assertThatThrownBy(() -> limiter.assertAllowed("a@x.com")).isInstanceOf(TooManyAttemptsException.class);

        clock.advance(Duration.ofMinutes(10)); // first attempt is now exactly 15 min old
        assertThatCode(() -> limiter.assertAllowed("a@x.com")).doesNotThrowAnyException();
    }

    @Test
    void resetClearsKey() {
        limiter.record("a@x.com");
        limiter.record("a@x.com");
        limiter.record("a@x.com");

        limiter.reset("a@x.com");

        assertThatCode(() -> limiter.assertAllowed("a@x.com")).doesNotThrowAnyException();
    }
}
