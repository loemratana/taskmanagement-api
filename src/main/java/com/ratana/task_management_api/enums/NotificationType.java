package com.ratana.task_management_api.enums;

public enum NotificationType {
    // Task
    TASK_ASSIGNED,
    TASK_UPDATED,
    TASK_COMPLETED,
    TASK_OVERDUE,
    TASK_COMMENTED,
    TASK_REMINDER,

    // Approval
    APPROVAL_REQUEST,
    APPROVAL_APPROVED,
    APPROVAL_REJECTED,

    // User
    USER_MENTIONED,

    // System
    SYSTEM_ANNOUNCEMENT,
    SYSTEM_MAINTENANCE,

}
