package com.ratana.task_management_api.announcement.repository.projection;

public interface MonthlyCountView {
    /** "yyyy-MM" */
    String getMonth();
    Long getCount();
}
