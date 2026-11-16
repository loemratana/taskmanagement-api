package com.ratana.task_management_api.infrastructure.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Starts Firebase (push notifications) from FIREBASE_CREDENTIALS: the service-account JSON itself or
 * that JSON base64-encoded. Without credentials the app still starts and push is skipped, so local
 * development and CI need no Firebase project; invalid credentials stop startup instead of failing
 * every push later.
 *
 * <p>This class was previously missing {@code @Configuration}, so Firebase was never initialised and
 * every push to a registered device failed with "FirebaseApp with name [DEFAULT] doesn't exist".
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.credentials:}")
    private String credentials;

    @PostConstruct
    void initialize() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }
        if (!StringUtils.hasText(credentials)) {
            log.warn("Push notifications are disabled: FIREBASE_CREDENTIALS is not set");
            return;
        }
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(serviceAccountJson(credentials))))
                .build();
        FirebaseApp.initializeApp(options);
        log.info("Firebase initialised: push notifications are enabled");
    }

    /** Accepts the raw JSON ("{...}") or its base64 encoding (line breaks allowed). */
    static byte[] serviceAccountJson(String value) {
        String trimmed = value.trim();
        return trimmed.startsWith("{")
                ? trimmed.getBytes(StandardCharsets.UTF_8)
                : Base64.getMimeDecoder().decode(trimmed);
    }

    /** False when no credentials were configured; senders then skip push. */
    public static boolean isEnabled() {
        return !FirebaseApp.getApps().isEmpty();
    }
}
