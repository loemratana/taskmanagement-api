package com.ratana.task_management_api.notification.enums;

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

    // Meeting room bookings
    MEETING_INVITED,
    MEETING_UPDATED,
    MEETING_CANCELLED,

    // Monthly / yearly top performers (performance awards)
    PERFORMANCE_AWARD,

}
