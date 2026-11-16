package com.ratana.task_management_api.announcement.entity;

import com.ratana.task_management_api.common.entity.BaseEntity;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.user.entity.User;


import com.ratana.task_management_api.announcement.enums.AnnouncementAudience;
import com.ratana.task_management_api.announcement.enums.AnnouncementPriority;
import com.ratana.task_management_api.announcement.enums.AnnouncementStatus;
import com.ratana.task_management_api.announcement.enums.AnnouncementType;
import com.ratana.task_management_api.announcement.enums.PublishMode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "announcements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Announcement extends BaseEntity {

    @Version
    @Builder.Default
    private Long version = 0L;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(length = 500)
    private String summary;

    /** Rich-text HTML from the editor; sanitize before saving. */
    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    @Builder.Default
    private AnnouncementType type = AnnouncementType.COMPANY;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    @Builder.Default
    private AnnouncementPriority priority = AnnouncementPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    @Builder.Default
    private AnnouncementStatus status = AnnouncementStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "publish_mode", length = 20, nullable = false)
    @Builder.Default
    private PublishMode publishMode = PublishMode.NOW;

    /** When it goes live (frontend startDate + publishTime combined). */
    @Column(name = "publish_at")
    private Instant publishAt;

    /** Null means it never expires. */
    @Column(name = "end_at")
    private Instant endAt;

    /** Actual time it was published, set by publish() or the scheduler. */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "banner_url", columnDefinition = "TEXT")
    private String bannerUrl;

    @Column(name = "banner_name", length = 255)
    private String bannerName;

    @Column(name = "attachment_url", columnDefinition = "TEXT")
    private String attachmentUrl;

    @Column(name = "attachment_name", length = 255)
    private String attachmentName;

    @Column(name = "send_in_app", nullable = false)
    @Builder.Default
    private boolean sendInApp = true;

    @Column(name = "send_push", nullable = false)
    @Builder.Default
    private boolean sendPush = false;

    @Column(name = "send_email", nullable = false)
    @Builder.Default
    private boolean sendEmail = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean pinned = false;

    /** Recipients resolved at publish time; denominator for read rate. */
    @Column(name = "recipient_count", nullable = false)
    @Builder.Default
    private int recipientCount = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    // ---- Audience ----

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type", length = 30, nullable = false)
    @Builder.Default
    private AnnouncementAudience audienceType = AnnouncementAudience.EVERYONE;

    /** audienceType = DEPARTMENT */
    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(
            name = "announcement_departments",
            joinColumns = @JoinColumn(name = "announcement_id"),
            inverseJoinColumns = @JoinColumn(name = "department_id")
    )
    @Builder.Default
    private Set<Department> targetDepartments = new HashSet<>();

    /** audienceType = TEAM: the managers whose direct reports receive it. */
    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(
            name = "announcement_team_managers",
            joinColumns = @JoinColumn(name = "announcement_id"),
            inverseJoinColumns = @JoinColumn(name = "manager_id")
    )
    @Builder.Default
    private Set<User> targetTeamManagers = new HashSet<>();

    /** audienceType = SELECTED_EMPLOYEES */
    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(
            name = "announcement_users",
            joinColumns = @JoinColumn(name = "announcement_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @Builder.Default
    private Set<User> targetUsers = new HashSet<>();

    // ---- Engagement ----

    @OneToMany(mappedBy = "announcement", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<AnnouncementRead> reads = new HashSet<>();


    /** Drops targets that don't belong to the current audience type. */
    public void clearUnusedAudience() {
        if (audienceType != AnnouncementAudience.DEPARTMENT) {
            targetDepartments.clear();
        }
        if (audienceType != AnnouncementAudience.TEAM) {
            targetTeamManagers.clear();
        }
        if (audienceType != AnnouncementAudience.SELECTED_EMPLOYEES) {
            targetUsers.clear();
        }
    }

    /** Not yet sent: edits may change timing and audience, and publishing is allowed. */
    public boolean isUnpublished() {
        return status == AnnouncementStatus.DRAFT || status == AnnouncementStatus.SCHEDULED;
    }
}
