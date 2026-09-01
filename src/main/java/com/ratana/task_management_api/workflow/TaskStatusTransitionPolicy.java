package com.ratana.task_management_api.workflow;

import com.ratana.task_management_api.enums.TaskStatus;
import com.ratana.task_management_api.exception.InvalidTaskTransitionException;
import com.ratana.task_management_api.exception.UnauthorizedTaskTransitionException;
import com.ratana.task_management_api.model.Task;
import com.ratana.task_management_api.security.UserPrincipal;
import com.ratana.task_management_api.service.ServiceImpl.TaskAuthorizationService;
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
