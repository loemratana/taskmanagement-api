package com.ratana.task_management_api.task.workflow;

import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Workflow facade: status graph + transition authz in one place for the domain service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskWorkflowService {

    private final TaskStatusTransitionPolicy transitionPolicy;

    /**
     * Admins follow the same graph as everyone else; their permissions already cover every scope.
     * Leaving the graph is only possible through the audited override endpoint (TaskServiceImpl.overrideStatus).
     */
    public void assertCanChangeStatus(Task task, TaskStatus targetStatus, UserPrincipal actor) {
        try {
            transitionPolicy.assertCanTransition(task, targetStatus, actor);
        } catch (RuntimeException e) {
            log.warn("Status transition denied: task={} {} -> {} by {}: {}",
                    task.getId(), task.getStatus(), targetStatus, actor.getId(), e.getMessage());
            throw e;
        }
        log.debug("Status transition allowed: task={} {} -> {} by {}",
                task.getId(), task.getStatus(), targetStatus, actor.getId());
    }
}
