package com.ratana.task_management_api.model;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Table(name = "refreshToken")
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false)
    private User user;


    @Column(
            name = "token_hash",
            nullable = false,
            unique = true,
            columnDefinition = "text"
    )
    private String tokenHash;


    @Column(name = "device_name", length = 100)
    private String deviceName;

    @Column(name = "device_type", length = 30)
    private String deviceType;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;


    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }


    public boolean isRevoked() {
        return revokedAt != null;
    }


    public boolean isValid() {
        return !isExpired() && !isRevoked();
    }

    public void revoke()
    {
        this.revokedAt = Instant.now();
    }

}
