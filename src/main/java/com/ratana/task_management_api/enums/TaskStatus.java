package com.ratana.task_management_api.enums;

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
            IN_PROGRESS, Set.of(IN_REVIEW, CANCELLED),
            IN_REVIEW, Set.of(APPROVED, REJECTED),
            REJECTED, Set.of(IN_PROGRESS, CANCELLED),
            APPROVED, Set.of(DONE),
            DONE, Set.of(),
            CANCELLED, Set.of()
    );

    public boolean canTransitionTo(TaskStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return this == DONE || this == CANCELLED;
    }
}
