package com.ratana.task_management_api.infrastructure.messaging;

import java.util.List;

/**
 * Thin wrapper over the Telegram Bot HTTP API. No business logic (department
 * resolution, message formatting) lives here — see TelegramGroupNotificationSender.
 *
 * <p>Every send method takes an optional {@code replyToMessageId}: when set, the message is posted
 * as a reply to that message in the same chat (Telegram still posts it if that message was
 * deleted). Send methods return the id of the (first) message posted, or null if Telegram
 * didn't report one. All of them throw TelegramApiException on failure.
 */
public interface TelegramBotClient {

    /** Sends an HTML-formatted message. */
    Long sendMessage(String chatId, String htmlText, Long replyToMessageId);

    default Long sendMessage(String chatId, String htmlText) {
        return sendMessage(chatId, htmlText, null);
    }

    /** Sends photos as album(s) of up to 10, with the HTML caption on the first photo. */
    Long sendPhotos(String chatId, List<TelegramFile> photos, String htmlCaption, Long replyToMessageId);

    default Long sendPhotos(String chatId, List<TelegramFile> photos, String htmlCaption) {
        return sendPhotos(chatId, photos, htmlCaption, null);
    }

    /** Sends photos without a caption, as album(s) of up to 10. */
    default Long sendPhotos(String chatId, List<TelegramFile> photos) {
        return sendPhotos(chatId, photos, null, null);
    }

    /** Uploads a file to the chat as a document. */
    Long sendDocument(String chatId, TelegramFile document, Long replyToMessageId);

    default Long sendDocument(String chatId, TelegramFile document) {
        return sendDocument(chatId, document, null);
    }

    /** Confirms the bot can currently post to the given chat. Never throws — returns false on any failure. */
    boolean verifyChat(String chatId);

    /** A file uploaded straight to Telegram (our storage is not reachable from Telegram's servers). */
    record TelegramFile(String fileName, String contentType, byte[] content) {
    }
}
