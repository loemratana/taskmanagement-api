package com.ratana.task_management_api.task.workflow;

public final class TaskPermissions {
    private TaskPermissions() {}

    // CRUD
    public static final String TASK_CREATE = "tasks:create";
    public static final String TASK_READ = "tasks:read";
    public static final String TASK_UPDATE = "tasks:update";
    public static final String TASK_UPDATE_OWN = "tasks:update:own";
    public static final String TASK_DELETE = "tasks:delete";

    // View
    public static final String TASK_VIEW_ALL = "tasks:view:all";
    public static final String TASK_VIEW_DEPARTMENT = "tasks:view:department";
    public static final String TASK_VIEW_TEAM = "tasks:view:team";
    public static final String TASK_VIEW_ASSIGNED = "tasks:view:assigned";

    // Assignment
    public static final String TASK_ASSIGN_ALL = "tasks:assign:all";
    public static final String TASK_ASSIGN_DEPARTMENT = "tasks:assign:department";
    public static final String TASK_ASSIGN_TEAM = "tasks:assign:team";

    // Workflow
    public static final String TASK_TRANSITION_ADVANCE_OWN = "tasks:transition:advance:own";

    public static final String TASK_TRANSITION_REVIEW_ALL = "tasks:transition:review:all";
    public static final String TASK_TRANSITION_REVIEW_DEPARTMENT = "tasks:transition:review:department";
    public static final String TASK_TRANSITION_REVIEW_TEAM = "tasks:transition:review:team";

    public static final String TASK_TRANSITION_CLOSE_ALL = "tasks:transition:close:all";
    public static final String TASK_TRANSITION_CLOSE_DEPARTMENT = "tasks:transition:close:department";
    public static final String TASK_TRANSITION_CLOSE_TEAM = "tasks:transition:close:team";

    public static final String TASK_TRANSITION_CANCEL_ALL = "tasks:transition:cancel:all";
    public static final String TASK_TRANSITION_CANCEL_DEPARTMENT = "tasks:transition:cancel:department";
    public static final String TASK_TRANSITION_CANCEL_TEAM = "tasks:transition:cancel:team";
    public static final String TASK_TRANSITION_REOPEN_ALL = "tasks:transition:reopen:all";
}

