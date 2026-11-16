package com.ratana.task_management_api.infrastructure.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Reading the posted message id from Telegram's response (object for most methods, array for albums). */
class TelegramBotClientImplTest {

    private static Long messageIdOf(Object result) throws Exception {
        Class<?> impl = Class.forName("com.ratana.task_management_api.infrastructure.messaging.TelegramBotClientImpl");
        Class<?> response = Class.forName(impl.getName() + "$TelegramApiResponse");
        Class<?> params = Class.forName(impl.getName() + "$TelegramResponseParameters");
        Constructor<?> ctor = response.getDeclaredConstructor(boolean.class, Integer.class, String.class, params, Object.class);
        ctor.setAccessible(true);
        Method messageId = impl.getDeclaredMethod("messageId", response);
        messageId.setAccessible(true);
        return (Long) messageId.invoke(null, ctor.newInstance(true, null, null, null, result));
    }

    @Test
    @DisplayName("sendMessage / sendPhoto / sendDocument: result is a Message")
    void singleMessage() throws Exception {
        assertThat(messageIdOf(Map.of("message_id", 42, "chat", Map.of("id", -100)))).isEqualTo(42L);
    }

    @Test
    @DisplayName("sendMediaGroup: result is an array; the first message leads the album")
    void album() throws Exception {
        assertThat(messageIdOf(List.of(Map.of("message_id", 7), Map.of("message_id", 8)))).isEqualTo(7L);
    }

    @Test
    @DisplayName("missing or odd results give null instead of failing the send")
    void missing() throws Exception {
        assertThat(messageIdOf(null)).isNull();
        assertThat(messageIdOf(List.of())).isNull();
        assertThat(messageIdOf(true)).isNull();
    }
}
