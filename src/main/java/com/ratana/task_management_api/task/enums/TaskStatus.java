package com.ratana.task_management_api.task.enums;

import java.util.List;
import java.util.Map;
import java.util.Set;

public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    APPROVED,
    REJECTED,
    DONE,
    BLOCKED,
    CANCELLED;



    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS = Map.of(
            TODO, Set.of(IN_PROGRESS, CANCELLED),
            IN_PROGRESS, Set.of(IN_REVIEW, BLOCKED, CANCELLED),
            // The assignee flags a blocker (reason required, TaskServiceImpl) and resumes when it clears.
            BLOCKED, Set.of(IN_PROGRESS, CANCELLED),
            IN_REVIEW, Set.of(APPROVED, REJECTED),
            REJECTED, Set.of(IN_PROGRESS, CANCELLED),
            APPROVED, Set.of(DONE),
            // Reopen; restricted to tasks:transition:reopen:all and a reason (TaskServiceImpl).
            DONE, Set.of(TODO),
            CANCELLED, Set.of(TODO)
    );

    public boolean canTransitionTo(TaskStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * Progress (0–100) a task earns by reaching this status. Rework (REJECTED) and BLOCKED sit at
     * the in-progress level; APPROVED counts as complete because DONE is only the closing step.
     */
    public int progressWeight() {
        return switch (this) {
            case TODO, CANCELLED -> 0;
            case IN_PROGRESS, REJECTED, BLOCKED -> 25;
            case IN_REVIEW -> 50;
            case APPROVED, DONE -> 100;
        };
    }

    public boolean isTerminal() {
        return this == DONE || this == CANCELLED;
    }

    /**
     * Statuses where the assignee has nothing left to do: finished, or waiting on a reviewer.
     * Reminders are not sent for these.
     */
    public static final List<TaskStatus> NO_REMINDER_STATUSES = List.of(IN_REVIEW, APPROVED, DONE, CANCELLED);

    public boolean needsReminders() {
        return !NO_REMINDER_STATUSES.contains(this);
    }
}
