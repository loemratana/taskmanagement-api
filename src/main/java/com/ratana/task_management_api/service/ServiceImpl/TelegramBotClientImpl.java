package com.ratana.task_management_api.service.ServiceImpl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ratana.task_management_api.config.TelegramProperties;
import com.ratana.task_management_api.exception.TelegramApiException;
import com.ratana.task_management_api.service.TelegramBotClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramBotClientImpl implements TelegramBotClient {

    private final RestClient telegramRestClient;
    private final TelegramProperties telegramProperties;

    @Override
    public void sendMessage(String chatId, String htmlText) {
        call("sendMessage", new SendMessageRequest(chatId, htmlText, "HTML", true));
    }

    @Override
    public boolean verifyChat(String chatId) {
        try {
            call("getChat", new ChatRequest(chatId));
            return true;
        } catch (TelegramApiException | RestClientException e) {
            log.warn("Telegram chat verification failed for chatId={}: {}", chatId, e.getMessage());
            return false;
        }
    }

    private TelegramApiResponse call(String method, Object body) {
        try {
            return telegramRestClient.post()
                    .uri("/bot{token}/{method}", telegramProperties.botToken(), method)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, response) -> {
                        TelegramApiResponse parsed = response.bodyTo(TelegramApiResponse.class);
                        if (response.getStatusCode().is2xxSuccessful() && parsed != null && parsed.ok()) {
                            return parsed;
                        }
                        throw toException(response.getStatusCode().value(), parsed);
                    });
        } catch (TelegramApiException e) {
            throw e;
        } catch (RestClientException e) {
            throw new TelegramApiException("Telegram request failed: " + e.getMessage(),
                    TelegramApiException.Kind.TRANSIENT, e);
        }
    }

    private TelegramApiException toException(int statusCode, TelegramApiResponse body) {
        String description = body != null && body.description() != null
                ? body.description()
                : "Telegram API error (HTTP " + statusCode + ")";

        if (statusCode == 429) {
            Long retryAfter = body != null && body.parameters() != null
                    ? body.parameters().retryAfter()
                    : null;
            return new TelegramApiException(description, TelegramApiException.Kind.TRANSIENT, retryAfter, null);
        }
        if (statusCode == 400 || statusCode == 401 || statusCode == 403 || statusCode == 404) {
            return new TelegramApiException(description, TelegramApiException.Kind.PERMANENT);
        }
        return new TelegramApiException(description, TelegramApiException.Kind.TRANSIENT);
    }

    private record SendMessageRequest(
            @JsonProperty("chat_id") String chatId,
            String text,
            @JsonProperty("parse_mode") String parseMode,
            @JsonProperty("disable_web_page_preview") boolean disableWebPagePreview) {
    }

    private record ChatRequest(@JsonProperty("chat_id") String chatId) {
    }

    private record TelegramApiResponse(
            boolean ok,
            @JsonProperty("error_code") Integer errorCode,
            String description,
            TelegramResponseParameters parameters) {
    }

    private record TelegramResponseParameters(@JsonProperty("retry_after") Long retryAfter) {
    }
}
