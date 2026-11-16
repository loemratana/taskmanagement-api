package com.ratana.task_management_api.task.event;

import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.entity.TaskAssignment;
import com.ratana.task_management_api.user.entity.User;
import lombok.Builder;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Something that happened to a task, published after the business operation succeeds.
 * Carries facts only — who gets notified and what they are told is decided by
 * NotificationService. Type-specific fields are null for other types; build events
 * through the static factories so each type always has what it needs.
 */
@Builder(access = lombok.AccessLevel.PRIVATE)
public record TaskEvent(
        TaskEventType type,
        UUID taskId,
        String taskTitle,
        UUID departmentId,
        UUID actorId,
        String actorName,
        UUID assigneeId,
        UUID creatorId,
        String oldStatus,       // STATUS_CHANGED, SUBMITTED, REVIEWED
        String newStatus,       // STATUS_CHANGED, SUBMITTED, REVIEWED
        String commentContent,  // COMMENT_ADDED: the comment; REVIEWED: the reviewer's comment; STATUS_CHANGED: the
                                // reason (block / cancel / reopen, may be null); DETAILS_CHANGED: what changed
        UUID submissionId,      // SUBMITTED, REVIEWED
        String lateReason,      // SUBMITTED: set only when submitted after the due date (a reason is then required)
        UUID parentTaskId,      // set when the task is a subtask (e.g. a group task's member subtask)
        UUID submittedById,     // SUBMITTED, REVIEWED: who submitted the work (may be a collaborator)
        List<UUID> collaboratorIds  // COLLABORATOR_ADDED: the people just added; otherwise every collaborator
) {

    public TaskEvent {
        collaboratorIds = collaboratorIds == null ? List.of() : List.copyOf(collaboratorIds);
    }

    public static TaskEvent assigned(Task task, UUID actorId, String actorName) {
        return base(TaskEventType.ASSIGNED, task, actorId, actorName).build();
    }

    public static TaskEvent commentAdded(Task task, UUID actorId, String actorName, String commentContent) {
        return base(TaskEventType.COMMENT_ADDED, task, actorId, actorName)
                .commentContent(commentContent)
                .collaboratorIds(collaboratorIdsOf(task))
                .build();
    }

    public static TaskEvent statusChanged(Task task, UUID actorId, String actorName,
                                          String oldStatus, String newStatus) {
        return statusChanged(task, actorId, actorName, oldStatus, newStatus, null);
    }

    /** {@code reason}: why it was blocked, cancelled or reopened; null when none was given. */
    public static TaskEvent statusChanged(Task task, UUID actorId, String actorName,
                                          String oldStatus, String newStatus, String reason) {
        return base(TaskEventType.STATUS_CHANGED, task, actorId, actorName)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .commentContent(reason)
                .collaboratorIds(collaboratorIdsOf(task))
                .build();
    }

    /** Title, priority or due date changed; {@code summary} says how, e.g. "due date 20 Nov → 25 Nov". */
    public static TaskEvent detailsChanged(Task task, UUID actorId, String actorName, String summary) {
        return base(TaskEventType.DETAILS_CHANGED, task, actorId, actorName)
                .commentContent(summary)
                .collaboratorIds(collaboratorIdsOf(task))
                .build();
    }

    /** The actor is the submitter. */
    public static TaskEvent submitted(Task task, UUID actorId, String actorName,
                                      String oldStatus, UUID submissionId, String lateReason) {
        return base(TaskEventType.SUBMITTED, task, actorId, actorName)
                .oldStatus(oldStatus)
                .newStatus(task.getStatus().name())
                .submissionId(submissionId)
                .lateReason(lateReason)
                .submittedById(actorId)
                .build();
    }

    /** A submission was approved or rejected; newStatus is where the task ended up (APPROVED, DONE or REJECTED). */
    public static TaskEvent reviewed(Task task, UUID actorId, String actorName, String oldStatus,
                                     UUID submissionId, UUID submittedById, String reviewComment) {
        return base(TaskEventType.REVIEWED, task, actorId, actorName)
                .oldStatus(oldStatus)
                .newStatus(task.getStatus().name())
                .commentContent(reviewComment)
                .submissionId(submissionId)
                .submittedById(submittedById)
                .collaboratorIds(collaboratorIdsOf(task))
                .build();
    }

    /** {@code addedUserIds} were just added as collaborators on the task. */
    public static TaskEvent collaboratorsAdded(Task task, UUID actorId, String actorName, List<UUID> addedUserIds) {
        return base(TaskEventType.COLLABORATOR_ADDED, task, actorId, actorName)
                .collaboratorIds(addedUserIds)
                .build();
    }

    /** The fields every event carries; each factory adds its type-specific ones. */
    private static TaskEventBuilder base(TaskEventType type, Task task, UUID actorId, String actorName) {
        return TaskEvent.builder()
                .type(type)
                .taskId(task.getId())
                .taskTitle(task.getTitle())
                .departmentId(task.getDepartment() != null ? task.getDepartment().getId() : null)
                .actorId(actorId)
                .actorName(actorName)
                .assigneeId(idOf(task.getAssignee()))
                .creatorId(idOf(task.getCreator()))
                .parentTaskId(task.getParentTask() != null ? task.getParentTask().getId() : null);
    }

    private static List<UUID> collaboratorIdsOf(Task task) {
        if (task.getAssignments() == null) {
            return List.of();
        }
        return task.getAssignments().stream()
                .map(TaskAssignment::getUser)
                .filter(Objects::nonNull)
                .map(User::getId)
                .distinct()
                .toList();
    }

    private static UUID idOf(User user) {
        return user != null ? user.getId() : null;
    }
}
