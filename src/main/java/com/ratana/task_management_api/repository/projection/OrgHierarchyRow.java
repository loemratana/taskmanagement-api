package com.ratana.task_management_api.repository.projection;

import java.util.UUID;

/**
 * Flat row shape shared by the recursive (WITH RECURSIVE) hierarchy queries in
 * {@link com.ratana.task_management_api.repository.UserRepository}. {@code level} is
 * relative to whatever root the query started from (0 = the starting node itself).
 */
public interface OrgHierarchyRow {
    UUID getId();
    String getFullName();
    String getEmail();
    String getAvatarUrl();
    UUID getPositionId();
    String getPositionName();
    UUID getDepartmentId();
    String getDepartmentName();
    UUID getManagerId();
    Integer getLevel();
}
