package com.ratana.task_management_api.notification.sender;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.ratana.task_management_api.infrastructure.firebase.FirebaseConfig;
import com.ratana.task_management_api.notification.enums.NotificationChannel;
import com.ratana.task_management_api.notification.enums.NotificationType;
import com.ratana.task_management_api.notification.entity.Notification;
import com.ratana.task_management_api.notification.entity.UserDevice;
import com.ratana.task_management_api.notification.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FirebaseNotificationSender implements NotificationSender {

    /**
     * Task reminders ring like an alarm clock. On Android they're sent data-only and the
     * mobile app's TaskAlarm module (modules/task-alarm) shows a full-screen, looping
     * alarm. iOS can't do that, so it gets a time-sensitive alert with the bundled
     * alarm sound (assets/sounds/alarm.wav in the mobile app).
     */
    private static final String REMINDER_IOS_SOUND = "alarm.wav";
    private static final long REMINDER_TTL_MS = 5 * 60 * 1000L;

    private final UserDeviceRepository userDeviceRepository;

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.PUSH;
    }

    @Override
    public void send(Notification notification) {
        if (!FirebaseConfig.isEnabled()) {
            // No FIREBASE_CREDENTIALS (local dev, CI): push is off, as logged once at startup.
            log.debug("Push disabled — skipping PUSH for notification {}", notification.getId());
            return;
        }
        List<UserDevice> devices = userDeviceRepository.findByUserIdAndActiveTrue(notification.getRecipientId());
        if (devices.isEmpty()) {
            log.debug("No active devices for user {} — skipping PUSH", notification.getRecipientId());
            return;
        }

        List<String> tokens = devices.stream()
                .map(UserDevice::getDeviceToken)
                .toList();

        boolean reminder = isReminder(notification);
        MulticastMessage.Builder builder = MulticastMessage.builder()
                .addAllTokens(tokens)
                .putData("notificationId", notification.getId().toString())
                .putData("type", notification.getType() != null ? notification.getType().name() : "")
                .putData("referenceType", notification.getReferenceType() != null ? notification.getReferenceType() : "")
                .putData("referenceId",
                        notification.getReferenceId() != null ? notification.getReferenceId().toString() : "")
                .putData("title", nullToEmpty(notification.getTitle()))
                .putData("body", nullToEmpty(notification.getMessage()))
                .setAndroidConfig(androidConfig(reminder))
                .setApnsConfig(apnsConfig(notification, reminder));

        // Reminders are data-only on Android: the app's background handler rings the
        // full-screen alarm itself, so the OS must not show its own notification too.
        // iOS gets its visible alert from the APNs payload instead (see apnsConfig).
        if (!reminder) {
            builder.setNotification(com.google.firebase.messaging.Notification.builder()
                    .setTitle(notification.getTitle())
                    .setBody(notification.getMessage())
                    .build());
        }
        MulticastMessage message = builder.build();

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            log.info("Push notification {} sent to user {}: {} succeeded, {} failed (reminder={})",
                    notification.getId(), notification.getRecipientId(),
                    response.getSuccessCount(), response.getFailureCount(), reminder);
            handleResponse(response, devices);
        } catch (FirebaseMessagingException e) {
            log.error("Firebase multicast failed for notification {}", notification.getId(), e);
            throw new IllegalStateException("FCM send failed", e);
        }
    }

    private static boolean isReminder(Notification notification) {
        return notification.getType() == NotificationType.TASK_REMINDER;
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private static AndroidConfig androidConfig(boolean reminder) {
        // HIGH priority wakes the device and lets the app start its alarm from the background.
        AndroidConfig.Builder builder = AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH);
        if (reminder) {
            // Don't ring a stale alarm hours later if the phone was offline when it was due.
            builder.setTtl(REMINDER_TTL_MS);
        }
        return builder.build();
    }

    private static ApnsConfig apnsConfig(Notification notification, boolean reminder) {
        Aps.Builder aps = Aps.builder().setSound(reminder ? REMINDER_IOS_SOUND : "default");
        if (reminder) {
            // No top-level notification for reminders, so iOS needs its alert spelled out here.
            aps.setAlert(ApsAlert.builder()
                    .setTitle(notification.getTitle())
                    .setBody(notification.getMessage())
                    .build());
            // Lets the reminder break through Focus modes (needs the app's time-sensitive entitlement).
            aps.putCustomData("interruption-level", "time-sensitive");
        }
        return ApnsConfig.builder().setAps(aps.build()).build();
    }

    private void handleResponse(BatchResponse response, List<UserDevice> devices) {
        List<String> invalidTokens = new ArrayList<>();

        for (int i = 0; i < response.getResponses().size(); i++) {
            SendResponse sendResponse = response.getResponses().get(i);
            if (!sendResponse.isSuccessful()) {
                MessagingErrorCode code = sendResponse.getException() != null
                        ? sendResponse.getException().getMessagingErrorCode()
                        : null;

                if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                    invalidTokens.add(devices.get(i).getDeviceToken());
                }
                log.warn("FCM failed for token {}: {}",
                        devices.get(i).getDeviceToken(), sendResponse.getException());
            }
        }

        invalidTokens.forEach(token -> {
            userDeviceRepository.deactivateByToken(token);
            log.info("Deactivated invalid FCM token: {}", token);
        });
    }
}
