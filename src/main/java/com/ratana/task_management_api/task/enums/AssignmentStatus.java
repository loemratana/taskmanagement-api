package com.ratana.task_management_api.task.enums;

public enum AssignmentStatus {
    ASSIGNED,

    ACCEPTED,

    IN_PROGRESS,

    COMPLETED,

    REJECTED;
    public boolean canTransitionTo(AssignmentStatus target) {
        if (this == target) return true;
        return switch (this) {
            case ASSIGNED -> target == ACCEPTED || target == REJECTED;
            case ACCEPTED -> target == IN_PROGRESS || target == REJECTED;
            case IN_PROGRESS -> target == COMPLETED || target == REJECTED;
            default -> false; // COMPLETED/REJECTED are terminal per assignment
        };
    }


}
