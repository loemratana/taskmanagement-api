package com.ratana.task_management_api.performance.service;

import com.ratana.task_management_api.notification.dto.request.NotificationRequest;
import com.ratana.task_management_api.notification.enums.NotificationChannel;
import com.ratana.task_management_api.notification.enums.NotificationPriority;
import com.ratana.task_management_api.notification.enums.NotificationType;
import com.ratana.task_management_api.notification.service.NotificationService;
import com.ratana.task_management_api.performance.event.PerformanceAwardPublishedEvent;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Tells every active member of the department about a new award: an in-app notification plus a
 * push to their phones. Runs only after the award is committed, so a rolled-back or duplicate
 * award never notifies anyone.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PerformanceAwardNotifier {

    static final String REFERENCE_TYPE = "PERFORMANCE_AWARD";

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPublished(PerformanceAwardPublishedEvent event) {
        List<User> recipients = userRepository.findByDepartmentId(event.departmentId()).stream()
                .filter(User::isAccountEnabled)
                .toList();
        int sent = 0;
        for (User recipient : recipients) {
            try {
                notificationService.send(NotificationRequest.builder()
                        .recipientId(recipient.getId())
                        // A system message: from the department manager, else "from" the recipient.
                        .senderId(event.managerId() != null ? event.managerId() : recipient.getId())
                        .title(event.title())
                        .message(event.message())
                        .type(NotificationType.PERFORMANCE_AWARD)
                        .priority(NotificationPriority.MEDIUM)
                        .referenceType(REFERENCE_TYPE)
                        .referenceId(event.awardId())
                        .metadata(Map.of("awardId", event.awardId().toString()))
                        .allowedChannels(EnumSet.of(NotificationChannel.IN_APP, NotificationChannel.PUSH))
                        .build());
                sent++;
            } catch (RuntimeException e) {
                log.warn("Award {} notification to {} failed: {}", event.awardId(), recipient.getId(), e.getMessage());
            }
        }
        log.info("Award {} notified {}/{} member(s) of department {}",
                event.awardId(), sent, recipients.size(), event.departmentId());
    }
}
