package com.ratana.task_management_api.notification.service;


import com.ratana.task_management_api.notification.dto.request.DeviceRegistrationRequest;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.notification.entity.UserDevice;
import com.ratana.task_management_api.notification.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserDeviceService {

    private final UserDeviceRepository userDeviceRepository;

    @Transactional
    public void registerDevice(UUID userId, DeviceRegistrationRequest request) {
        userDeviceRepository.findByDeviceToken(request.getDeviceToken())
                .ifPresentOrElse(existing -> {
                    // Re-activate / update ownership
                    existing.setUserId(userId);
                    existing.setPlatform(request.getDeviceType());
                    existing.setActive(true);
                    existing.setLastActiveAt(Instant.now());
                    userDeviceRepository.save(existing);
                    log.info("Device {} re-registered for user {} ({})", existing.getId(), userId, request.getDeviceType());
                }, () -> {
                    UserDevice device = UserDevice.builder()
                            .userId(userId)
                            .deviceToken(request.getDeviceToken())
                            .platform(request.getDeviceType())
                            .active(true)
                            .lastActiveAt(Instant.now())
                            .build();
                    UserDevice saved = userDeviceRepository.save(device);
                    log.info("New device {} registered for user {} ({})", saved.getId(), userId, request.getDeviceType());
                });
    }

    @Transactional
    public void deactivateDevice(UUID userId, String token) {
        int updated = userDeviceRepository.deactivateByUserAndToken(userId, token);
        if (updated == 0) {
            log.warn("Device deactivation failed for user {}: device not found or already inactive", userId);
            throw new ResourceNotFoundException("Device not found or already inactive");
        }
        log.info("Device deactivated for user {}", userId);
    }
}
