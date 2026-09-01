package com.ratana.task_management_api.service.sender;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.ratana.task_management_api.enums.NotificationChannel;
import com.ratana.task_management_api.model.Notification;
import com.ratana.task_management_api.model.UserDevice;
import com.ratana.task_management_api.repository.UserDeviceRepository;
import com.ratana.task_management_api.service.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FirebaseNotificationSender implements NotificationSender {

    private final UserDeviceRepository userDeviceRepository;

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.PUSH;
    }

    @Override
    public void send(Notification notification) {
        List<UserDevice> devices = userDeviceRepository.findByUserIdAndActiveTrue(notification.getRecipientId());
        if (devices.isEmpty()) {
            log.debug("No active devices for user {} — skipping PUSH", notification.getRecipientId());
            return;
        }

        List<String> tokens = devices.stream()
                .map(UserDevice::getDeviceToken)
                .toList();

        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(notification.getTitle())
                        .setBody(notification.getMessage())
                        .build())
                .putData("notificationId", notification.getId().toString())
                .putData("type", notification.getType() != null ? notification.getType().name() : "")
                .putData("referenceType", notification.getReferenceType() != null ? notification.getReferenceType() : "")
                .putData("referenceId",
                        notification.getReferenceId() != null ? notification.getReferenceId().toString() : "")
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder().setSound("default").build())
                        .build())
                .build();

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            handleResponse(response, devices);
        } catch (FirebaseMessagingException e) {
            log.error("Firebase multicast failed for notification {}", notification.getId(), e);
            throw new IllegalStateException("FCM send failed", e);
        }
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
