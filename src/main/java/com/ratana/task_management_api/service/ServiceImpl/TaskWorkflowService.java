package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.enums.TaskStatus;
import com.ratana.task_management_api.model.Task;
import com.ratana.task_management_api.security.UserPrincipal;
import com.ratana.task_management_api.workflow.TaskStatusTransitionPolicy;
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

    public void assertCanChangeStatus(Task task, TaskStatus targetStatus, UserPrincipal actor) {
        transitionPolicy.assertCanTransition(task, targetStatus, actor);
        log.debug("Status transition allowed: task={} {} -> {} by {}",
                task.getId(), task.getStatus(), targetStatus, actor.getId());
    }
}
