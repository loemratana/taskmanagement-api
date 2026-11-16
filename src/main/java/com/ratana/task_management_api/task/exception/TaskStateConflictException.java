package com.ratana.task_management_api.task.exception;

import lombok.Getter;

/**
 * The request is well-formed and permitted, but the task's current state does not allow it
 * (e.g. editing a finished task, or setting IN_REVIEW without a submission). Maps to 409 with
 * a stable {@link #getErrorCode() error code}.
 */
@Getter
public class TaskStateConflictException extends RuntimeException {

    public static final String TASK_NOT_EDITABLE = "TASK_NOT_EDITABLE";
    public static final String TASK_TERMINAL = "TASK_TERMINAL";
    public static final String USE_SUBMISSION_ENDPOINT = "USE_SUBMISSION_ENDPOINT";
    public static final String USE_REVIEW_ENDPOINT = "USE_REVIEW_ENDPOINT";
    /** A group task's status and assignment are driven by its member subtasks. */
    public static final String GROUP_TASK_MANAGED = "GROUP_TASK_MANAGED";

    private final String errorCode;

    public TaskStateConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
