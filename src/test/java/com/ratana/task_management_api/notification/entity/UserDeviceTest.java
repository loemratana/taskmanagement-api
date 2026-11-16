package com.ratana.task_management_api.notification.entity;

import com.ratana.task_management_api.notification.enums.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserDeviceTest {

    @Test
    @DisplayName("a device built like UserDeviceService does has its NOT NULL timestamps set")
    void builder_keepsTimestampDefaults() {
        UserDevice device = UserDevice.builder()
                .userId(UUID.randomUUID())
                .deviceToken("token")
                .platform(Platform.ANDROID)
                .active(true)
                .build();

        assertThat(device.getCreatedAt()).isNotNull();
        assertThat(device.getUpdatedAt()).isNotNull();
        assertThat(device.getDeviceInfo()).isNotNull();
    }
}
