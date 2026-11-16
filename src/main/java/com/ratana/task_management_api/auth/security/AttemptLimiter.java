package com.ratana.task_management_api.auth.security;

import com.ratana.task_management_api.auth.exception.TooManyAttemptsException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window limiter: at most {@code maxAttempts} recorded attempts per key within
 * {@code window}; past that the key is blocked until its oldest attempt leaves the window.
 * <p>
 * State lives in this JVM only. That is enough for a single API instance; running several
 * instances behind a load balancer needs a shared store (e.g. Redis) instead.
 */
public class AttemptLimiter {

    private final String name;
    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public AttemptLimiter(String name, int maxAttempts, Duration window, Clock clock) {
        this.name = name;
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    /** Throws {@link TooManyAttemptsException} when {@code key} has used up its attempts. */
    public void assertAllowed(String key) {
        Deque<Instant> history = attempts.get(key);
        if (history == null) {
            return;
        }
        synchronized (history) {
            Instant now = clock.instant();
            prune(history, now);
            if (history.size() >= maxAttempts) {
                long retryAfter = Math.max(1, Duration.between(now, history.peekFirst().plus(window)).toSeconds());
                throw new TooManyAttemptsException(name, retryAfter);
            }
        }
    }

    public void record(String key) {
        Deque<Instant> history = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (history) {
            Instant now = clock.instant();
            prune(history, now);
            history.addLast(now);
        }
    }

    public void reset(String key) {
        attempts.remove(key);
    }

    /** Drops keys whose attempts have all expired, so memory doesn't grow with one-off keys. */
    public void evictExpired() {
        Instant now = clock.instant();
        attempts.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue(), now);
                return entry.getValue().isEmpty();
            }
        });
    }

    private void prune(Deque<Instant> history, Instant now) {
        Instant cutoff = now.minus(window);
        while (!history.isEmpty() && !history.peekFirst().isAfter(cutoff)) {
            history.pollFirst();
        }
    }
}
