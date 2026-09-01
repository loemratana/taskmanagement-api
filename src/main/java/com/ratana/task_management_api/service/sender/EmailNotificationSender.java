package com.ratana.task_management_api.service.sender;

import com.ratana.task_management_api.enums.NotificationChannel;
import com.ratana.task_management_api.model.Notification;
import com.ratana.task_management_api.model.User;
import com.ratana.task_management_api.repository.UserRepository;
import com.ratana.task_management_api.service.NotificationPreferenceService;
import com.ratana.task_management_api.service.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.notification.email.enabled", havingValue = "true")
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final NotificationPreferenceService preferenceService;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(Notification notification) {
        User recipient = userRepository.findById(notification.getRecipientId())
                .orElseThrow(() -> new IllegalStateException(
                        "Recipient not found for email notification: " + notification.getRecipientId()));

        String to = resolveToAddress(recipient, notification);
        if (!StringUtils.hasText(to)) {
            throw new IllegalStateException("No email address for recipient " + recipient.getId());
        }

        SimpleMailMessage message = new SimpleMailMessage();
        if (StringUtils.hasText(fromAddress)) {
            message.setFrom(fromAddress);
        }
        message.setTo(to);
        message.setSubject(notification.getTitle());
        message.setText(notification.getMessage());
        mailSender.send(message);

        log.debug("Email notification sent to {} for notification {}", to, notification.getId());
    }

    private String resolveToAddress(User recipient, Notification notification) {
        Map<String, Object> config = preferenceService.resolveChannelConfig(
                notification.getRecipientId(), NotificationChannel.EMAIL);
        Object configured = config.get("email");
        if (configured instanceof String email && StringUtils.hasText(email)) {
            return email;
        }
        return recipient.getEmail();
    }
}
