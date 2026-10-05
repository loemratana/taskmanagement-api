package com.ratana.task_management_api.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Push notifications are best-effort: if Firebase credentials are missing or fail to
 * load, {@link #firebaseMessaging()} returns an empty Optional instead of throwing, so
 * the app still starts. FirebaseNotificationSender then fails PUSH sends through the
 * normal notification retry/dead-letter path rather than the whole context refusing to boot.
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    private static final String LOCAL_CREDENTIALS_FILE = "firebase-service-account.json";

    /** Base64-encoded (preferred) or raw JSON service-account key. */
    @Value("${firebase.credentials:}")
    private String firebaseCredentialsJson;

    @Bean
    public Optional<FirebaseMessaging> firebaseMessaging() {
        try (InputStream serviceAccount = resolveCredentials()) {
            if (serviceAccount == null) {
                log.warn("Firebase not configured (set FIREBASE_CREDENTIALS, or place {} on the classpath "
                        + "for local dev) — push notifications will fail through the normal retry path.",
                        LOCAL_CREDENTIALS_FILE);
                return Optional.empty();
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            return Optional.of(FirebaseMessaging.getInstance(app));
        } catch (IOException | RuntimeException e) {
            log.error("Failed to initialize Firebase — push notifications will fail through the normal retry path.", e);
            return Optional.empty();
        }
    }

    private InputStream resolveCredentials() throws IOException {
        if (StringUtils.hasText(firebaseCredentialsJson)) {
            return new ByteArrayInputStream(decode(firebaseCredentialsJson.trim()));
        }
        ClassPathResource resource = new ClassPathResource(LOCAL_CREDENTIALS_FILE);
        return resource.exists() ? resource.getInputStream() : null;
    }

    /** Accepts either a raw JSON service-account key or a base64-encoded one. */
    private byte[] decode(String value) {
        if (value.startsWith("{")) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
    }
}
