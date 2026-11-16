package com.ratana.task_management_api.announcement.mapper;

import com.ratana.task_management_api.announcement.dto.request.AnnouncementRequest;
import com.ratana.task_management_api.announcement.dto.response.AnnouncementFeedResponse;
import com.ratana.task_management_api.announcement.dto.response.AnnouncementResponse;
import com.ratana.task_management_api.common.response.Summary;
import com.ratana.task_management_api.announcement.enums.AnnouncementStatus;
import com.ratana.task_management_api.announcement.entity.Announcement;
import com.ratana.task_management_api.announcement.entity.AnnouncementRead;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.announcement.repository.projection.AnnouncementMetrics;
import com.ratana.task_management_api.infrastructure.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scalar fields only. Audience targets, createdBy, status and publishAt for NOW
 * are resolved in the service (they need repositories / business rules).
 *
 * Banner / attachment columns hold a MinIO storage key (or an external https URL);
 * responses carry both the key (sent back on edit) and a short-lived signed URL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementMapper {

    private static final Duration SIGNED_URL_TTL = Duration.ofHours(1);

    private final StorageService storageService;

    public Announcement toEntity(AnnouncementRequest request) {
        Announcement announcement = Announcement.builder().build(); // keeps @Builder.Default values
        apply(announcement, request);
        return announcement;
    }

    /** PUT = full replace of the editable fields. */
    public void updateEntity(Announcement announcement, AnnouncementRequest request) {
        apply(announcement, request);
    }

    /** "Duplicate": new draft with the same content and audience, no reads. */
    public Announcement copyAsDraft(Announcement source, User createdBy) {
        return Announcement.builder()
                .title(source.getTitle() + " (Copy)")
                .summary(source.getSummary())
                .description(source.getDescription())
                .type(source.getType())
                .priority(source.getPriority())
                .status(AnnouncementStatus.DRAFT)
                .publishMode(source.getPublishMode())
                .publishAt(source.getPublishAt())
                .endAt(source.getEndAt())
                .bannerUrl(source.getBannerUrl())
                .bannerName(source.getBannerName())
                .attachmentUrl(source.getAttachmentUrl())
                .attachmentName(source.getAttachmentName())
                .sendInApp(source.isSendInApp())
                .sendPush(source.isSendPush())
                .sendEmail(source.isSendEmail())
                .pinned(source.isPinned())
                .audienceType(source.getAudienceType())
                .targetDepartments(new HashSet<>(source.getTargetDepartments()))
                .targetTeamManagers(new HashSet<>(source.getTargetTeamManagers()))
                .targetUsers(new HashSet<>(source.getTargetUsers()))
                .createdBy(createdBy)
                .build();
    }

    public AnnouncementResponse toResponse(Announcement a) {
        return toResponse(a, null);
    }

    public AnnouncementResponse toResponse(Announcement a, AnnouncementMetrics metrics) {
        return AnnouncementResponse.builder()
                .id(a.getId())
                .version(a.getVersion())
                .title(a.getTitle())
                .summary(a.getSummary())
                .description(a.getDescription())
                .type(a.getType())
                .priority(a.getPriority())
                .status(a.getStatus())
                .publishMode(a.getPublishMode())
                .publishAt(a.getPublishAt())
                .endAt(a.getEndAt())
                .publishedAt(a.getPublishedAt())
                .bannerImage(resolveUrl(a.getBannerUrl()))
                .bannerImageKey(a.getBannerUrl())
                .bannerImageName(a.getBannerName())
                .attachmentUrl(resolveUrl(a.getAttachmentUrl()))
                .attachmentKey(a.getAttachmentUrl())
                .attachmentName(a.getAttachmentName())
                .audienceType(a.getAudienceType())
                .departments(departmentSummaries(a.getTargetDepartments()))
                .teams(userSummaries(a.getTargetTeamManagers()))
                .users(userSummaries(a.getTargetUsers()))
                .sendInAppNotification(a.isSendInApp())
                .sendPushNotification(a.isSendPush())
                .sendEmail(a.isSendEmail())
                .pinToDashboard(a.isPinned())
                .views(metrics == null || metrics.getViews() == null ? 0 : metrics.getViews())
                .readCount(metrics == null || metrics.getReadCount() == null ? 0 : metrics.getReadCount())
                .createdBy(userSummary(a.getCreatedBy()))
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    /** @param read the current user's read row, or null if never opened */
    public AnnouncementFeedResponse toFeedResponse(Announcement a, AnnouncementRead read) {
        return AnnouncementFeedResponse.builder()
                .id(a.getId())
                .title(a.getTitle())
                .summary(a.getSummary())
                .description(a.getDescription())
                .type(a.getType())
                .priority(a.getPriority())
                .bannerImage(resolveUrl(a.getBannerUrl()))
                .attachmentUrl(resolveUrl(a.getAttachmentUrl()))
                .attachmentName(a.getAttachmentName())
                .publishedAt(a.getPublishedAt())
                .endAt(a.getEndAt())
                .pinned(a.isPinned())
                .createdBy(userSummary(a.getCreatedBy()))
                .read(read != null && read.getReadAt() != null)
                .readAt(read == null ? null : read.getReadAt())
                .build();
    }

    // ---- helpers ----

    private void apply(Announcement a, AnnouncementRequest r) {
        a.setTitle(r.getTitle().trim());
        a.setSummary(trimToNull(r.getSummary()));
        a.setDescription(r.getDescription()); // sanitized in the service
        a.setType(r.getType());
        a.setPriority(r.getPriority());
        a.setPublishMode(r.getPublishMode());
        a.setPublishAt(r.getPublishAt());
        a.setEndAt(r.getEndAt());
        a.setBannerUrl(trimToNull(r.getBannerImage()));
        a.setBannerName(trimToNull(r.getBannerImageName()));
        a.setAttachmentUrl(trimToNull(r.getAttachmentUrl()));
        a.setAttachmentName(trimToNull(r.getAttachmentName()));
        a.setAudienceType(r.getAudienceType());
        a.setSendInApp(r.isSendInAppNotification());
        a.setSendPush(r.isSendPushNotification());
        a.setSendEmail(r.isSendEmail());
        a.setPinned(r.isPinToDashboard());
    }

    private Set<Summary> departmentSummaries(Collection<Department> departments) {
        return departments.stream()
                .map(d -> new Summary(d.getId(), d.getName()))
                .collect(Collectors.toSet());
    }

    private Set<Summary> userSummaries(Collection<User> users) {
        return users.stream()
                .map(this::userSummary)
                .collect(Collectors.toSet());
    }

    private Summary userSummary(User user) {
        return user == null ? null : new Summary(user.getId(), user.getFullName());
    }

    /** External URLs pass through; storage keys become signed URLs. */
    private String resolveUrl(String stored) {
        if (!StringUtils.hasText(stored)) {
            return null;
        }
        if (stored.startsWith("http://") || stored.startsWith("https://")) {
            return stored;
        }
        try {
            return storageService.createSignedUrl(stored, SIGNED_URL_TTL);
        } catch (Exception e) {
            log.warn("Could not sign announcement file {}: {}", stored, e.getMessage());
            return null;
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
