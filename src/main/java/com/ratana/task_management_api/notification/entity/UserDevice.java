package com.ratana.task_management_api.notification.entity;


import com.ratana.task_management_api.notification.enums.Platform;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "user_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // @Builder.Default: without it Lombok's builder leaves these null, and the NOT NULL columns
    // rejected every new device registration (UserDeviceService builds new devices).
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_token", nullable = false, length = 512, unique = true)
    private String deviceToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false)
    @Builder.Default
    private Platform platform = Platform.UNKNOWN; // ANDROID, IOS, WEB

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "device_info", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> deviceInfo = new HashMap<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;
}
