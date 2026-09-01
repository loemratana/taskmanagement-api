package com.ratana.task_management_api.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "department_telegram_groups",
        uniqueConstraints = @UniqueConstraint(name = "uq_dept_telegram_group_department", columnNames = "department_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentTelegramGroup extends BaseEnitty {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false, unique = true)
    private Department department;

    /**
     * Telegram group chat ids are large negative longs (e.g. -1001234567890) —
     * stored as text to avoid overflow/formatting surprises round-tripping through JSON.
     */
    @Column(name = "chat_id", nullable = false, length = 64)
    private String chatId;

    @Column(name = "group_name", length = 255)
    private String groupName;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Last time a send (or an explicit getChat check) to this chat succeeded. */
    @Column(name = "verified_at")
    private Instant verifiedAt;
}
