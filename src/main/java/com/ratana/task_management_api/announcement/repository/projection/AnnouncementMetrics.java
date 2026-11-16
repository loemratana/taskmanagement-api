package com.ratana.task_management_api.announcement.repository.projection;

import java.util.UUID;

public interface AnnouncementMetrics {
    UUID getAnnouncementId();
    Long getViews();
    Long getReadCount();
}
