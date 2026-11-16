package com.ratana.task_management_api.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetMailerTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final PasswordResetMailer mailer = new PasswordResetMailer(provider, "noreply@example.com");

    @Test
    @DisplayName("Emails the code from the configured address")
    void sendCode_sendsMessage() {
        when(provider.getIfAvailable()).thenReturn(sender);

        mailer.sendCode(UUID.randomUUID(), "emp@example.com", "Emp One", "123456", 15);

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("emp@example.com");
        assertThat(mail.getValue().getFrom()).isEqualTo("noreply@example.com");
        assertThat(mail.getValue().getText()).contains("Hello Emp One").contains("code is: 123456").contains("15 minutes");
    }

    @Test
    @DisplayName("No mail setup or a failing server never throws")
    void sendCode_neverThrows() {
        when(provider.getIfAvailable()).thenReturn(null);
        assertThatCode(() -> mailer.sendCode(UUID.randomUUID(), "emp@example.com", "Emp", "123456", 15))
                .doesNotThrowAnyException();

        when(provider.getIfAvailable()).thenReturn(sender);
        doThrow(new MailSendException("Authentication failed")).when(sender).send(any(SimpleMailMessage.class));
        assertThatCode(() -> mailer.sendCode(UUID.randomUUID(), "emp@example.com", "Emp", "123456", 15))
                .doesNotThrowAnyException();
    }
}
