package com.ratana.task_management_api.announcement.repository.projection;

import java.util.UUID;

public interface MostViewedView {
    UUID getId();
    String getTitle();
    Long getViews();
}
