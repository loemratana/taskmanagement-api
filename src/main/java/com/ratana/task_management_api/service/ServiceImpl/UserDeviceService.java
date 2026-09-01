package com.ratana.task_management_api.service.ServiceImpl;


import com.ratana.task_management_api.dto.request.DeviceRegistrationRequest;
import com.ratana.task_management_api.exception.NotificationException;
import com.ratana.task_management_api.model.UserDevice;
import com.ratana.task_management_api.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
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
                }, () -> {
                    UserDevice device = UserDevice.builder()
                            .userId(userId)
                            .deviceToken(request.getDeviceToken())
                            .platform(request.getDeviceType())
                            .active(true)
                            .lastActiveAt(Instant.now())
                            .build();
                    userDeviceRepository.save(device);
                });
    }

    @Transactional
    public void deactivateDevice(UUID userId, String token) {
        int updated = userDeviceRepository.deactivateByUserAndToken(userId, token);
        if (updated == 0) {
            throw new NotificationException("Device not found or already inactive");
        }
    }
}
