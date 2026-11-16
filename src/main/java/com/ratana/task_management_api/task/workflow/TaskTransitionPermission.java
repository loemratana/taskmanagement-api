package com.ratana.task_management_api.task.workflow;

/**
 * @deprecated Use {@link TaskPermissions} — single permission vocabulary for the product.
 * Kept as aliases so existing imports compile during migration.
 */
@Deprecated
public final class TaskTransitionPermission {

    private TaskTransitionPermission() {}

    public static final String ADVANCE_OWN = TaskPermissions.TASK_TRANSITION_ADVANCE_OWN;
    public static final String REVIEW_DEPARTMENT = TaskPermissions.TASK_TRANSITION_REVIEW_DEPARTMENT;
    public static final String REVIEW_ALL = TaskPermissions.TASK_TRANSITION_REVIEW_ALL;
    public static final String CLOSE_DEPARTMENT = TaskPermissions.TASK_TRANSITION_CLOSE_DEPARTMENT;
    public static final String CLOSE_ALL = TaskPermissions.TASK_TRANSITION_CLOSE_ALL;
    public static final String CANCEL_DEPARTMENT = TaskPermissions.TASK_TRANSITION_CANCEL_DEPARTMENT;
    public static final String CANCEL_ALL = TaskPermissions.TASK_TRANSITION_CANCEL_ALL;
    public static final String REOPEN_ALL = TaskPermissions.TASK_TRANSITION_REOPEN_ALL;
}
