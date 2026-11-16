package com.ratana.task_management_api.announcement.entity;

import com.ratana.task_management_api.common.entity.BaseEntity;
import com.ratana.task_management_api.user.entity.User;


import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * One row per (announcement, user). Views = rows, readCount = rows with readAt set.
 */
@Entity
@Table(
        name = "announcement_reads",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_announcement_read_user",
                columnNames = {"announcement_id", "user_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnouncementRead extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "announcement_id", nullable = false)
    private Announcement announcement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "first_viewed_at", nullable = false)
    private Instant firstViewedAt;

    @Column(name = "last_viewed_at", nullable = false)
    private Instant lastViewedAt;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private int viewCount = 1;

    /** Null until the user explicitly marks it read (or opens the detail view). */
    @Column(name = "read_at")
    private Instant readAt;
}
