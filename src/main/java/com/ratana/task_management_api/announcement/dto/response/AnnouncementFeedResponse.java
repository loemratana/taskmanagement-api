package com.ratana.task_management_api.announcement.dto.response;

import com.ratana.task_management_api.common.response.Summary;

import com.ratana.task_management_api.announcement.enums.AnnouncementPriority;
import com.ratana.task_management_api.announcement.enums.AnnouncementType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** What an employee sees: no audience, delivery flags, or metrics. */
@Schema(description = "Announcement as shown in an employee's feed")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementFeedResponse {
    private UUID id;
    private String title;
    private String summary;
    private String description;
    private AnnouncementType type;
    private AnnouncementPriority priority;

    private String bannerImage;
    private String attachmentUrl;
    private String attachmentName;

    private Instant publishedAt;
    private Instant endAt;
    private boolean pinned;

    private Summary createdBy;

    /** For the current user. */
    private boolean read;
    private Instant readAt;
}
