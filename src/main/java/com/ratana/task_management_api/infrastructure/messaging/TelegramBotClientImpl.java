package com.ratana.task_management_api.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TelegramBotClientImpl implements TelegramBotClient {

    private static final int MAX_ALBUM_SIZE = 10;
    /** Telegram's limit for a media caption. */
    private static final int CAPTION_LIMIT = 1024;

    private final RestClient telegramRestClient;
    private final RestClient telegramUploadRestClient;
    private final TelegramProperties telegramProperties;

    // Explicit constructor: Lombok does not copy @Qualifier onto generated constructor params.
    public TelegramBotClientImpl(@Qualifier("telegramRestClient") RestClient telegramRestClient,
                                 @Qualifier("telegramUploadRestClient") RestClient telegramUploadRestClient,
                                 TelegramProperties telegramProperties) {
        this.telegramRestClient = telegramRestClient;
        this.telegramUploadRestClient = telegramUploadRestClient;
        this.telegramProperties = telegramProperties;
    }

    @Override
    public Long sendMessage(String chatId, String htmlText, Long replyToMessageId) {
        TelegramApiResponse response = call("sendMessage",
                new SendMessageRequest(chatId, htmlText, "HTML", true, ReplyParameters.of(replyToMessageId)));
        Long messageId = messageId(response);
        log.debug("Telegram message {} sent to chatId={} (reply to {})", messageId, chatId, replyToMessageId);
        return messageId;
    }

    @Override
    public Long sendPhotos(String chatId, List<TelegramFile> photos, String htmlCaption, Long replyToMessageId) {
        // Trim defensively — Telegram rejects captions over 1024 characters.
        String caption = htmlCaption == null ? null : truncateHtml(htmlCaption, CAPTION_LIMIT);
        long totalStarted = System.nanoTime();
        Long firstMessageId = null;

        for (int start = 0; start < photos.size(); start += MAX_ALBUM_SIZE) {
            List<TelegramFile> chunk = photos.subList(start, Math.min(start + MAX_ALBUM_SIZE, photos.size()));
            boolean first = start == 0;
            long started = System.nanoTime();

            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("chat_id", chatId);
            if (replyToMessageId != null) {
                // Every album replies to the same message, so the whole set stays in the thread.
                form.add("reply_parameters", replyParametersJson(replyToMessageId));
            }

            TelegramApiResponse response;
            if (chunk.size() == 1) {
                form.add("photo", filePart(chunk.get(0)));
                if (caption != null && first) {
                    form.add("caption", caption);
                    form.add("parse_mode", "HTML");
                }
                response = upload("sendPhoto", form);
            } else {
                List<String> media = new ArrayList<>();
                for (int i = 0; i < chunk.size(); i++) {
                    StringBuilder item = new StringBuilder()
                            .append("{\"type\":\"photo\",\"media\":\"attach://photo").append(i).append("\"");
                    if (i == 0 && caption != null && first) {
                        item.append(",\"caption\":").append(jsonString(caption))
                                .append(",\"parse_mode\":\"HTML\"");
                    }
                    item.append("}");
                    media.add(item.toString());
                    form.add("photo" + i, filePart(chunk.get(i)));
                }
                form.add("media", "[" + String.join(",", media) + "]");
                response = upload("sendMediaGroup", form);
            }

            if (first) {
                firstMessageId = messageId(response);
            }
            log.info("Telegram photo batch completed: count={}, size={} KB, duration={} ms",
                    chunk.size(),
                    chunk.stream().mapToLong(file -> file.content().length).sum() / 1024,
                    (System.nanoTime() - started) / 1_000_000);
        }
        log.debug("Telegram sent {} photo(s) to chatId={} in {} ms", photos.size(), chatId,
                (System.nanoTime() - totalStarted) / 1_000_000);
        return firstMessageId;
    }

    @Override
    public Long sendDocument(String chatId, TelegramFile document, Long replyToMessageId) {
        long started = System.nanoTime();
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("chat_id", chatId);
        form.add("document", filePart(document));
        if (replyToMessageId != null) {
            form.add("reply_parameters", replyParametersJson(replyToMessageId));
        }
        Long messageId = messageId(upload("sendDocument", form));
        log.info("Telegram document completed: name={}, size={} KB, duration={} ms",
                document.fileName(),
                document.content().length / 1024,
                (System.nanoTime() - started) / 1_000_000);
        return messageId;
    }

    @Override
    public boolean verifyChat(String chatId) {
        try {
            call("getChat", new ChatRequest(chatId));
            log.debug("Telegram chat verified: chatId={}", chatId);
            return true;
        } catch (TelegramApiException | RestClientException e) {
            log.warn("Telegram chat verification failed for chatId={}: {}", chatId, e.getMessage());
            return false;
        }
    }

    private TelegramApiResponse call(String method, Object body) {
        return execute(telegramRestClient, method, MediaType.APPLICATION_JSON, body);
    }

    /** Multipart uploads go through a client with a longer read timeout. */
    private TelegramApiResponse upload(String method, MultiValueMap<String, Object> form) {
        return execute(telegramUploadRestClient, method, MediaType.MULTIPART_FORM_DATA, form);
    }

    /**
     * The posted message's id. {@code result} is a Message for sendMessage/sendPhoto/sendDocument
     * and an array of Messages for sendMediaGroup (the first one is the album's lead message).
     */
    static Long messageId(TelegramApiResponse response) {
        Object result = response != null ? response.result() : null;
        if (result instanceof List<?> list) {
            result = list.isEmpty() ? null : list.get(0);
        }
        if (result instanceof Map<?, ?> message && message.get("message_id") instanceof Number id) {
            return id.longValue();
        }
        return null;
    }

    /** Multipart form field value; JSON requests use {@link ReplyParameters} instead. */
    private static String replyParametersJson(long messageId) {
        return "{\"message_id\":" + messageId + ",\"allow_sending_without_reply\":true}";
    }

    private static String jsonString(String s) {
        // minimal JSON escape for caption content
        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    private static String truncateHtml(String html, int max) {
        if (html.length() <= max) {
            return html;
        }
        // naive: cut at max-1, append ellipsis. Acceptable for a notification caption;
        // callers already trim the long parts (description, note) upstream.
        return html.substring(0, max - 1) + "…";
    }

    private TelegramApiResponse execute(RestClient client, String method, MediaType contentType, Object body) {
        try {
            return client.post()
                    .uri("/bot{token}/{method}", telegramProperties.botToken(), method)
                    .contentType(contentType)
                    .body(body)
                    .exchange((request, response) -> {
                        TelegramApiResponse parsed = response.bodyTo(TelegramApiResponse.class);
                        if (response.getStatusCode().is2xxSuccessful() && parsed != null && parsed.ok()) {
                            return parsed;
                        }
                        throw toException(response.getStatusCode().value(), parsed);
                    });
        } catch (TelegramApiException e) {
            log.warn("Telegram API call {} failed ({}): {}", method, e.getKind(), e.getMessage());
            throw e;
        } catch (RestClientException e) {
            log.warn("Telegram API call {} failed: {}", method, e.getMessage());
            throw new TelegramApiException("Telegram request failed: " + e.getMessage(),
                    TelegramApiException.Kind.TRANSIENT, e);
        }
    }

    private static HttpEntity<ByteArrayResource> filePart(TelegramFile file) {
        String fileName = file.fileName() != null && !file.fileName().isBlank() ? file.fileName() : "file";
        ByteArrayResource resource = new ByteArrayResource(file.content()) {
            @Override
            public String getFilename() {
                return fileName;   // without a filename the part is not sent as a file upload
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(file.contentType() != null
                ? MediaType.parseMediaType(file.contentType())
                : MediaType.APPLICATION_OCTET_STREAM);
        return new HttpEntity<>(resource, headers);
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SendMessageRequest(
            @JsonProperty("chat_id") String chatId,
            String text,
            @JsonProperty("parse_mode") String parseMode,
            @JsonProperty("disable_web_page_preview") boolean disableWebPagePreview,
            @JsonProperty("reply_parameters") ReplyParameters replyParameters) {
    }

    /** allow_sending_without_reply: still post if the original message was deleted. */
    private record ReplyParameters(
            @JsonProperty("message_id") long messageId,
            @JsonProperty("allow_sending_without_reply") boolean allowSendingWithoutReply) {

        static ReplyParameters of(Long messageId) {
            return messageId != null ? new ReplyParameters(messageId, true) : null;
        }
    }

    private record ChatRequest(@JsonProperty("chat_id") String chatId) {
    }

    /** {@code result} is left untyped: a Message object or an array of them, depending on the method. */
    record TelegramApiResponse(
            boolean ok,
            @JsonProperty("error_code") Integer errorCode,
            String description,
            TelegramResponseParameters parameters,
            Object result) {
    }

    record TelegramResponseParameters(@JsonProperty("retry_after") Long retryAfter) {
    }
}
