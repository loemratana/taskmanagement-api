package com.ratana.task_management_api.announcement.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ratana.task_management_api.announcement.enums.AnnouncementAudience;
import com.ratana.task_management_api.announcement.enums.AnnouncementPriority;
import com.ratana.task_management_api.announcement.enums.AnnouncementType;
import com.ratana.task_management_api.announcement.enums.PublishMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Create and update (PUT) share this payload: the admin form always submits every field.
 */
@Schema(description = "Payload to create or update an announcement")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 500)
    private String summary;

    /** Rich-text HTML; sanitized in the service. */
    @Size(max = 100_000)
    private String description;

    @NotNull
    private AnnouncementType type;

    @NotNull
    private AnnouncementPriority priority;

    @NotNull
    private PublishMode publishMode;

    /** Required when publishMode = SCHEDULED; frontend combines startDate + publishTime. */
    private Instant publishAt;

    private Instant endAt;

    /** MinIO storageKey from /api/v1/files/upload, or an external https URL. */
    @Size(max = 1000)
    private String bannerImage;
    @Size(max = 255)
    private String bannerImageName;

    /** MinIO storageKey from /api/v1/files/upload, or an external https URL. */
    @Size(max = 1000)
    private String attachmentUrl;
    @Size(max = 255)
    private String attachmentName;

    @NotNull
    private AnnouncementAudience audienceType;

    @Size(max = 100)
    private Set<UUID> departmentIds = new HashSet<>();

    /** Manager ids: their direct reports form the "team". */
    @Size(max = 100)
    private Set<UUID> teamIds = new HashSet<>();

    @Size(max = 1000)
    private Set<UUID> userIds = new HashSet<>();

    private boolean sendInAppNotification = true;
    private boolean sendPushNotification;
    private boolean sendEmail;
    private boolean pinToDashboard;

    /** Save without publishing, regardless of publishMode. */
    private boolean draft;


    @JsonIgnore
    @AssertTrue(message = "publishAt is required when publishMode is SCHEDULED")
    public boolean isScheduleValid() {
        return publishMode != PublishMode.SCHEDULED || publishAt != null;
    }

    @JsonIgnore
    @AssertTrue(message = "endAt must be after publishAt")
    public boolean isDateRangeValid() {
        return endAt == null || publishAt == null || endAt.isAfter(publishAt);
    }

    @JsonIgnore
    @AssertTrue(message = "Select at least one target for the chosen audience")
    public boolean isAudienceValid() {
        if (audienceType == null) {
            return true; // reported by @NotNull
        }
        return switch (audienceType) {
            case EVERYONE -> true;
            case DEPARTMENT -> departmentIds != null && !departmentIds.isEmpty();
            case TEAM -> teamIds != null && !teamIds.isEmpty();
            case SELECTED_EMPLOYEES -> userIds != null && !userIds.isEmpty();
        };
    }
}
