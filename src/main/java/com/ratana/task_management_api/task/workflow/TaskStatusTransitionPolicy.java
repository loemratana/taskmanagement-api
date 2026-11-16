package com.ratana.task_management_api.task.workflow;

import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.task.exception.InvalidTaskTransitionException;
import com.ratana.task_management_api.task.exception.UnauthorizedTaskTransitionException;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.task.service.TaskAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validates status graph edges, then delegates scope/permission checks to {@link TaskAuthorizationService}.
 */
@Component
@RequiredArgsConstructor
public class TaskStatusTransitionPolicy {

    private final TaskAuthorizationService authorizationService;

    public void assertCanTransition(Task task, TaskStatus targetStatus, UserPrincipal currentUser) {
        TaskStatus currentStatus = task.getStatus();

        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new InvalidTaskTransitionException(
                    "Task " + task.getId() + " cannot transition from " + currentStatus + " to " + targetStatus);
        }

        if (!authorizationService.canTransition(currentUser, task, targetStatus)) {
            throw new UnauthorizedTaskTransitionException(
                    "You do not have permission to move this task from %s to %s."
                            .formatted(currentStatus, targetStatus));
        }
    }
}
