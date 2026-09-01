package com.ratana.task_management_api.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.telegram")
public record TelegramProperties(
        String botToken,
        String apiBaseUrl,
        int connectTimeoutMs,
        int readTimeoutMs
) {
}
