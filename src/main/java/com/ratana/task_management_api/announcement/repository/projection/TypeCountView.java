package com.ratana.task_management_api.announcement.repository.projection;

import com.ratana.task_management_api.announcement.enums.AnnouncementType;

public interface TypeCountView {
    AnnouncementType getType();
    Long getCount();
}
