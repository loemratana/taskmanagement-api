package com.ratana.task_management_api.announcement.dto.request;

import com.ratana.task_management_api.announcement.enums.AnnouncementAudience;
import com.ratana.task_management_api.announcement.enums.AnnouncementPriority;
import com.ratana.task_management_api.announcement.enums.AnnouncementStatus;
import com.ratana.task_management_api.announcement.enums.AnnouncementType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnnouncementFilterRequest {
    private AnnouncementStatus status;
    private AnnouncementType type;
    private AnnouncementPriority priority;
    private AnnouncementAudience audienceType;
    private Boolean pinned;
    private UUID createdById;
    /** Matches title or summary. */
    private String search;
    /** publishAt range. */
    private Instant from;
    private Instant to;
}
