package com.ratana.task_management_api.infrastructure.messaging;

/**
 * Raised by the Telegram integration layer. {@code kind} tells callers (the sender,
 * the dispatcher) whether the failure is worth retrying or should go straight to
 * DEAD_LETTER — e.g. a 429 is TRANSIENT, "bot was kicked from the group" is PERMANENT.
 */
public class TelegramApiException extends RuntimeException {

    public enum Kind {
        TRANSIENT,
        PERMANENT
    }

    private final Kind kind;

    /** Populated only when Telegram returned a 429 with a Retry-After hint. */
    private final Long retryAfterSeconds;

    public TelegramApiException(String message, Kind kind) {
        this(message, kind, null, null);
    }

    public TelegramApiException(String message, Kind kind, Throwable cause) {
        this(message, kind, null, cause);
    }

    public TelegramApiException(String message, Kind kind, Long retryAfterSeconds, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public boolean isRetryable() {
        return kind == Kind.TRANSIENT;
    }

    public Kind getKind() {
        return kind;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
