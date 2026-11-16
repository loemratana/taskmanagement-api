package com.ratana.task_management_api.announcement.dto.response;

import com.ratana.task_management_api.common.response.Summary;

import com.ratana.task_management_api.announcement.enums.AnnouncementAudience;
import com.ratana.task_management_api.announcement.enums.AnnouncementPriority;
import com.ratana.task_management_api.announcement.enums.AnnouncementStatus;
import com.ratana.task_management_api.announcement.enums.AnnouncementType;
import com.ratana.task_management_api.announcement.enums.PublishMode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Schema(description = "Announcement details (admin view)")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementResponse {
    private UUID id;
    private Long version;

    private String title;
    private String summary;
    private String description;
    private AnnouncementType type;
    private AnnouncementPriority priority;
    private AnnouncementStatus status;

    private PublishMode publishMode;
    private Instant publishAt;
    private Instant endAt;
    private Instant publishedAt;

    /** Signed URL (expires after ~1h) for display. */
    private String bannerImage;
    /** Storage key; send this back as bannerImage when editing. */
    private String bannerImageKey;
    private String bannerImageName;
    /** Signed URL (expires after ~1h) for download. */
    private String attachmentUrl;
    /** Storage key; send this back as attachmentUrl when editing. */
    private String attachmentKey;
    private String attachmentName;

    private AnnouncementAudience audienceType;
    /** Summaries instead of bare ids so the UI can label them without extra lookups. */
    private Set<Summary> departments;
    private Set<Summary> teams;
    private Set<Summary> users;

    private boolean sendInAppNotification;
    private boolean sendPushNotification;
    private boolean sendEmail;
    private boolean pinToDashboard;

    /** Sum of view_count across readers. */
    private long views;
    /** Users who marked it read. */
    private long readCount;

    private Summary createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
