package com.ratana.task_management_api.infrastructure.messaging;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.telegram")
public record TelegramProperties(
        String botToken,
        String apiBaseUrl,
        int connectTimeoutMs,
        int readTimeoutMs,
        int uploadReadTimeoutMs
) {
}
