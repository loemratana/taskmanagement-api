package com.ratana.task_management_api.task.service;

import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.enums.TaskAuditAction;
import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Keeps a group task's status in step with its member subtasks. A group task has no assignee and
 * nobody submits on it; its status is derived:
 * <ul>
 *   <li>every member approved/done → DONE (auto-close)</li>
 *   <li>every member still TODO → TODO</li>
 *   <li>otherwise → IN_PROGRESS</li>
 *   <li>every member cancelled → CANCELLED</li>
 * </ul>
 * Archived members are ignored, cancelled ones don't block closing. A member reopened after the
 * group closed reopens the group. Progress needs no syncing: Task#computeProgress averages members.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskGroupService {

    private final TaskRepository taskRepository;
    private final TaskAuditService taskAuditService;

    /** Call after any status/active change of {@code member}; a no-op unless its parent is a group task. */
    @Transactional
    public void onMemberChanged(Task member, UUID actorId) {
        Task parent = member.getParentTask();
        if (parent != null && parent.isGroupTask()) {
            sync(parent, actorId);
        }
    }

    @Transactional
    public void sync(Task group, UUID actorId) {
        TaskStatus target = derivedStatus(group);
        if (target == null || target == group.getStatus()) {
            return;
        }
        String oldStatus = group.getStatus() != null ? group.getStatus().name() : null;
        group.setStatus(target);
        taskRepository.save(group);
        log.info("Group task {} status {} -> {} (derived from members)", group.getId(), oldStatus, target);
        taskAuditService.record(group, actorId, TaskAuditAction.GROUP_STATUS_SYNCED,
                TaskAuditService.values("status", oldStatus),
                TaskAuditService.values("status", target.name()),
                target == TaskStatus.DONE
                        ? "Closed automatically: every member's work is approved."
                        : "Updated automatically from member subtasks.");
    }

    /** Null when there is nothing to derive from (no active members), so the status is left alone. */
    static TaskStatus derivedStatus(Task group) {
        List<Task> active = group.getSubtasks() == null ? List.of()
                : group.getSubtasks().stream().filter(Task::isActive).toList();
        if (active.isEmpty()) {
            return null;
        }
        List<Task> members = group.countableSubtasks();
        if (members.isEmpty()) {
            return TaskStatus.CANCELLED;
        }
        if (members.stream().allMatch(m -> m.getStatus() == TaskStatus.APPROVED || m.getStatus() == TaskStatus.DONE)) {
            return TaskStatus.DONE;
        }
        if (members.stream().allMatch(m -> m.getStatus() == TaskStatus.TODO)) {
            return TaskStatus.TODO;
        }
        return TaskStatus.IN_PROGRESS;
    }
}
