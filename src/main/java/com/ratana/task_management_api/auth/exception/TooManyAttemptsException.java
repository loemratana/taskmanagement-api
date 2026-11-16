package com.ratana.task_management_api.auth.exception;

import lombok.Getter;

/** Rate limit hit on a public auth endpoint. Mapped to 429 with a Retry-After header. */
@Getter
public class TooManyAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(String action, long retryAfterSeconds) {
        super("Too many " + action + " attempts. Try again in " + toMinutes(retryAfterSeconds) + ".");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    private static String toMinutes(long seconds) {
        long minutes = Math.max(1, (seconds + 59) / 60);
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }
}
