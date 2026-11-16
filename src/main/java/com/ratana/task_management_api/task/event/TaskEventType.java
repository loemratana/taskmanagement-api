package com.ratana.task_management_api.task.event;

public enum TaskEventType {
    ASSIGNED,
    COMMENT_ADDED,
    STATUS_CHANGED,
    SUBMITTED,
    /** A submission was approved or rejected. */
    REVIEWED,
    /** People were added as collaborators on a (shared) task; collaboratorIds holds just them. */
    COLLABORATOR_ADDED,
    /** Title, priority or due date was edited; commentContent summarises the change. */
    DETAILS_CHANGED
}
