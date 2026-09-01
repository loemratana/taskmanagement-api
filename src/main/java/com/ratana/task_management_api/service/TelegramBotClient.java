package com.ratana.task_management_api.service;

/**
 * Thin wrapper over the Telegram Bot HTTP API. No business logic (department
 * resolution, message formatting) lives here — see TelegramGroupNotificationSender.
 */
public interface TelegramBotClient {

    /** Sends an HTML-formatted message to the given chat. Throws TelegramApiException on failure. */
    void sendMessage(String chatId, String htmlText);

    /** Confirms the bot can currently post to the given chat. Never throws — returns false on any failure. */
    boolean verifyChat(String chatId);
}
