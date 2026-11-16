package com.ratana.task_management_api.announcement.service;

import com.ratana.task_management_api.notification.service.NotificationService;

import com.ratana.task_management_api.notification.dto.request.NotificationRequest;
import com.ratana.task_management_api.announcement.enums.AnnouncementPriority;
import com.ratana.task_management_api.announcement.enums.AnnouncementStatus;
import com.ratana.task_management_api.notification.enums.NotificationChannel;
import com.ratana.task_management_api.notification.enums.NotificationPriority;
import com.ratana.task_management_api.notification.enums.NotificationType;
import com.ratana.task_management_api.announcement.entity.Announcement;
import com.ratana.task_management_api.announcement.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Sends one SYSTEM_ANNOUNCEMENT notification per recipient, limited to the channels the author ticked. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementNotifier {

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementRecipientResolver recipientResolver;
    private final NotificationService notificationService;

    /**
     * @Transactional is required: NotificationService.send registers an after-commit
     * dispatch hook that needs an active transaction.
     */
    @Transactional
    public void notifyRecipients(UUID announcementId, UUID actorId) {
        Announcement announcement = announcementRepository.findWithDetailsById(announcementId).orElse(null);
        if (announcement == null || announcement.getStatus() != AnnouncementStatus.PUBLISHED) {
            log.info("Skipping notifications for announcement {}: missing or no longer published", announcementId);
            return;
        }

        Set<NotificationChannel> channels = channelsOf(announcement);
        if (channels.isEmpty()) {
            log.info("Announcement {} published with every notification channel off", announcementId);
            return;
        }

        UUID senderId = actorId != null ? actorId : announcement.getCreatedBy().getId();
        Set<UUID> recipients = recipientResolver.resolve(announcement);
        String message = StringUtils.hasText(announcement.getSummary())
                ? announcement.getSummary()
                : announcement.getTitle();

        int sent = 0;
        for (UUID recipientId : recipients) {
            try {
                notificationService.send(NotificationRequest.builder()
                        .recipientId(recipientId)
                        .senderId(senderId)
                        .title(announcement.getTitle())
                        .message(message)
                        .type(NotificationType.SYSTEM_ANNOUNCEMENT)
                        .priority(toNotificationPriority(announcement.getPriority()))
                        .referenceType("ANNOUNCEMENT")
                        .referenceId(announcement.getId())
                        .metadata(Map.of(
                                "announcementType", announcement.getType().name(),
                                "pinned", announcement.isPinned()))
                        .forcedChannels(announcement.isSendEmail() ? EnumSet.of(NotificationChannel.EMAIL) : null)
                        .allowedChannels(channels)
                        .build());
                sent++;
            } catch (Exception e) {
                log.warn("Announcement {} notification to {} failed: {}", announcementId, recipientId, e.getMessage());
            }
        }
        log.info("Announcement {} notified {}/{} recipient(s) on {}", announcementId, sent, recipients.size(), channels);
    }

    private Set<NotificationChannel> channelsOf(Announcement a) {
        Set<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        if (a.isSendInApp()) {
            channels.add(NotificationChannel.IN_APP);
        }
        if (a.isSendPush()) {
            channels.add(NotificationChannel.PUSH);
        }
        if (a.isSendEmail()) {
            channels.add(NotificationChannel.EMAIL);
        }
        return channels;
    }

    private NotificationPriority toNotificationPriority(AnnouncementPriority priority) {
        return switch (priority) {
            case LOW -> NotificationPriority.LOW;
            case MEDIUM -> NotificationPriority.MEDIUM;
            case HIGH -> NotificationPriority.HIGH;
            case CRITICAL -> NotificationPriority.URGENT;
        };
    }
}
