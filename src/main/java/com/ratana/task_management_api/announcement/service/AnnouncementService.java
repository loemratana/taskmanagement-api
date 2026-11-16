package com.ratana.task_management_api.announcement.service;

import com.ratana.task_management_api.announcement.dto.request.AnnouncementFilterRequest;
import com.ratana.task_management_api.announcement.dto.request.AnnouncementRequest;
import com.ratana.task_management_api.announcement.dto.response.AnnouncementAnalyticsResponse;
import com.ratana.task_management_api.announcement.dto.response.AnnouncementFeedResponse;
import com.ratana.task_management_api.announcement.dto.response.AnnouncementResponse;
import com.ratana.task_management_api.announcement.dto.response.AnnouncementStatsResponse;
import com.ratana.task_management_api.common.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.Set;
import java.util.UUID;

public interface AnnouncementService {

    // ---- Admin ----

    /** publishMode NOW publishes immediately unless draft=true; SCHEDULED waits for publishAt. */
    AnnouncementResponse create(AnnouncementRequest request, UUID actorId);

    AnnouncementResponse update(UUID id, AnnouncementRequest request, UUID actorId);

    AnnouncementResponse getById(UUID id);

    PageResponse<AnnouncementResponse> search(AnnouncementFilterRequest filter, Pageable pageable);

    /** Draft or scheduled -> published now, and notifies the audience. */
    AnnouncementResponse publish(UUID id, UUID actorId);

    /** Publishes every draft/scheduled one among ids; returns how many were published. */
    int publishAll(Set<UUID> ids, UUID actorId);

    AnnouncementResponse archive(UUID id);

    AnnouncementResponse duplicate(UUID id, UUID actorId);

    void delete(UUID id);

    int deleteAll(Set<UUID> ids);

    AnnouncementStatsResponse getStats();

    AnnouncementAnalyticsResponse getAnalytics(UUID currentUserId);

    // ---- Employee ----

    PageResponse<AnnouncementFeedResponse> getFeed(UUID userId, Pageable pageable);

    /** Records a view. 404 unless the announcement is live and targets the user. */
    AnnouncementFeedResponse viewFromFeed(UUID id, UUID userId);

    void markRead(UUID id, UUID userId);

    // ---- Scheduler ----

    /** Publishes one scheduled announcement if it is still due; false if it was skipped. */
    boolean publishIfDue(UUID id);

    int expireDue();
}
