package com.ratana.task_management_api.model;


import com.ratana.task_management_api.enums.DeliveryStatus;
import com.ratana.task_management_api.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_deliveries",
        indexes = {
                @Index(name = "idx_deliveries_notification_id", columnList = "notification_id"),
                @Index(name = "idx_deliveries_status", columnList = "status"),
                @Index(name = "idx_deliveries_channel", columnList = "channel"),
                @Index(name = "idx_deliveries_attempts", columnList = "attempts")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;



    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "sent_at")
    private Instant sentAt;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // Business method: increment attempts and update timestamp
    public void incrementAttempts() {
        this.attempts++;
        this.lastAttemptAt = Instant.now();
    }
}
