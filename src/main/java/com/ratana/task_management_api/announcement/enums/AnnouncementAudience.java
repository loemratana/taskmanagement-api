package com.ratana.task_management_api.announcement.enums;

public enum AnnouncementAudience {
    EVERYONE,
    DEPARTMENT,
    /** Direct reports of the selected managers (the backend has no Team entity). */
    TEAM,
    SELECTED_EMPLOYEES
}
