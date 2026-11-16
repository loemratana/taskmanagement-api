package com.ratana.task_management_api.announcement.event;

import java.util.UUID;

/** Raised inside the publishing transaction; handled after commit. */
public record AnnouncementPublishedEvent(UUID announcementId, UUID actorId) {
}
