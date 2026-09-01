package com.ratana.task_management_api.enums;

/**
 * How widely a user may list/view tasks.
 * Resolved from permissions — never from role name strings in services.
 */
public enum ViewScope {
    /** Assignee or creator only (employee). */
    OWN,
    /** Direct reports' tasks + own (team manager). */
    TEAM,
    /** Same department (department manager). */
    DEPARTMENT,
    /** Everything (admin). */
    ALL
}
