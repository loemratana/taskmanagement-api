package com.ratana.task_management_api.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Emails password reset codes in the background. Sending through SMTP takes a second or more
 * (longer if the server is slow), and doing it inside the request would also make "forgot password"
 * answer slower for real accounts than for unknown emails, revealing which emails exist.
 */
@Slf4j
@Component
public class PasswordResetMailer {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String fromAddress;

    public PasswordResetMailer(ObjectProvider<JavaMailSender> mailSender,
                               @Value("${spring.mail.username:}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    /** Never throws: a failure is logged (the user can ask for a new code). */
    @Async("notificationExecutor")
    public void sendCode(UUID userId, String email, String fullName, String code, long ttlMinutes) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.error("Password reset code for user {} not sent: no mail sender configured (SMTP_*)", userId);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        if (StringUtils.hasText(fromAddress)) {
            message.setFrom(fromAddress);
        }
        message.setTo(email);
        message.setSubject("Your password reset code");
        message.setText("""
                Hello %s,

                Your password reset code is: %s

                It expires in %d minutes. If you didn't ask to reset your password, you can ignore this email.
                """.formatted(fullName, code, ttlMinutes));
        try {
            sender.send(message);
            log.info("Password reset email sent to user {}", userId);
        } catch (MailException ex) {
            log.error("Password reset email to user {} failed: {}", userId, ex.getMessage());
        }
    }
}
