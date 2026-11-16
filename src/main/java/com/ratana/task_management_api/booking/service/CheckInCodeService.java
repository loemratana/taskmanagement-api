package com.ratana.task_management_api.booking.service;

import com.ratana.task_management_api.booking.config.BookingProperties;
import com.ratana.task_management_api.common.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

/**
 * Rotating QR codes for meeting check-in.
 *
 * <p>Payload: {@code tms-checkin:v1:<bookingId>:<token>}, where the token is an HMAC of the
 * booking id and the current time window ({@code checkInCodeSeconds} long). The code shown
 * a moment ago is still accepted, so a scan just as it rotates works; a photo of the code
 * sent to someone outside the room stops working within a minute.
 */
@Component
public class CheckInCodeService {

    private static final String PREFIX = "tms-checkin:v1:";
    private static final String ALGORITHM = "HmacSHA256";
    /** 16 bytes of the MAC is plenty for a code that lives one minute. */
    private static final int TOKEN_BYTES = 16;

    private final byte[] secret;
    private final int periodSeconds;

    public CheckInCodeService(BookingProperties properties) {
        if (!StringUtils.hasText(properties.checkInSecret())) {
            throw new IllegalStateException("app.booking.check-in-secret must be set");
        }
        this.secret = properties.checkInSecret().getBytes(StandardCharsets.UTF_8);
        this.periodSeconds = Math.max(properties.checkInCodeSeconds(), 5);
    }

    /** A code to display now, and when it should be replaced. */
    public record Code(String payload, Instant expiresAt, int refreshSeconds) {
    }

    /** The parts of a scanned payload. */
    public record Scanned(UUID bookingId, String token) {
    }

    public Code issue(UUID bookingId, Instant now) {
        long window = windowOf(now);
        return new Code(PREFIX + bookingId + ":" + token(bookingId, window),
                Instant.ofEpochSecond((window + 1) * periodSeconds), periodSeconds);
    }

    /** Parses what the scanner read; rejects anything that isn't a check-in code. */
    public Scanned parse(String payload) {
        if (payload == null || !payload.startsWith(PREFIX)) {
            throw new BadRequestException("This is not a meeting check-in code");
        }
        String[] parts = payload.substring(PREFIX.length()).split(":");
        if (parts.length != 2) {
            throw new BadRequestException("This is not a meeting check-in code");
        }
        try {
            return new Scanned(UUID.fromString(parts[0]), parts[1]);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("This is not a meeting check-in code");
        }
    }

    /** True when the token was issued for this booking in the current or previous window. */
    public boolean isValid(Scanned scanned, Instant now) {
        long window = windowOf(now);
        byte[] given = scanned.token().getBytes(StandardCharsets.US_ASCII);
        return matches(given, token(scanned.bookingId(), window))
                || matches(given, token(scanned.bookingId(), window - 1));
    }

    private long windowOf(Instant now) {
        return now.getEpochSecond() / periodSeconds;
    }

    private String token(UUID bookingId, long window) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            byte[] digest = mac.doFinal((bookingId + "|" + window).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(digest, TOKEN_BYTES));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }

    private static boolean matches(byte[] given, String expected) {
        return MessageDigest.isEqual(given, expected.getBytes(StandardCharsets.US_ASCII));
    }
}
