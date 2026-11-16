package com.ratana.task_management_api.announcement.repository.projection;

import java.util.UUID;

public interface DepartmentEngagementView {
    UUID getDepartmentId();
    String getDepartment();
    /** Active users in the department. */
    Long getMembers();
    /** Distinct members who read at least one announcement. */
    Long getReaders();
}
