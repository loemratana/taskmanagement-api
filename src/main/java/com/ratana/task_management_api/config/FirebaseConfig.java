package com.ratana.task_management_api.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class FirebaseConfig {

    @Value("${firebase.credentials:}")
    private String firebaseCredentialsJson;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        InputStream serviceAccount;
        if (firebaseCredentialsJson != null && !firebaseCredentialsJson.isEmpty()) {
            // Read from environment variable (base64 or plain JSON)
            serviceAccount = new ByteArrayInputStream(firebaseCredentialsJson.getBytes(StandardCharsets.UTF_8));
        } else {
            // Fallback to classpath file (for local development)
            serviceAccount = new ClassPathResource(firebaseCredentialsJson).getInputStream();
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.initializeApp(options);
        }
        return FirebaseApp.getInstance();
    }

    @Bean
    public FirebaseMessaging firebaseMessaging() throws IOException{

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .build();
            FirebaseApp.initializeApp(options);

        }
        return FirebaseMessaging.getInstance();
    }
}
