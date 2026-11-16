package com.ratana.task_management_api.task.enums;

/** Stored in task_logs.action_type (varchar 50). Append new values; never rename existing ones. */
public enum TaskAuditAction {
    TASK_CREATED,
    TASK_ARCHIVED,
    TASK_UPDATED,
    ASSIGNEE_CHANGED,
    COLLABORATORS_ADDED,
    STATUS_CHANGED,
    STATUS_OVERRIDDEN,
    TASK_REOPENED,
    WORK_SUBMITTED,
    SUBMISSION_REVIEWED,
    COMMENT_ADDED,
    ATTACHMENT_ADDED,
    ATTACHMENT_DELETED,
    REMINDER_SETTINGS_CHANGED,
    /** A group task's status was recomputed from its member subtasks. */
    GROUP_STATUS_SYNCED,
    /** A person was added to a group task (a member subtask was created). */
    GROUP_MEMBER_ADDED
}
