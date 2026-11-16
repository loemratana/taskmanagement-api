package com.ratana.task_management_api.task.service;

import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.task.enums.ViewScope;
import com.ratana.task_management_api.task.exception.UnauthorizedTaskAccessException;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.task.workflow.TaskPermissions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Single gate for resource-scoped task authorization.
 * Controllers may use coarse {@code @PreAuthorize}; this service owns dept/team/assignee rules.
 *
 * <p>"Team" scope (managers): tasks the actor created or is assigned to, plus tasks whose
 * primary assignee reports directly to the actor (User.manager). Reviewing is limited to
 * direct reports' tasks, so a manager never approves their own work.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskAuthorizationService {

    private final TaskPermissionService permissionService;

    public boolean canCreateTask(UserPrincipal actor) {
        return permissionService.hasPermission(actor, TaskPermissions.TASK_CREATE);
    }

    public void assertCanCreate(UserPrincipal actor) {
        if (!canCreateTask(actor)) {
            log.warn("Access denied: user {} may not create tasks", actor.getId());
            throw new UnauthorizedTaskAccessException("Not authorized to create tasks.");
        }
    }

    /** targetUser is the employee being considered for assignment. */
    public boolean canAssignTask(UserPrincipal actor, Task task, User targetUser) {
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_ASSIGN_ALL)) {
            return true;
        }
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_ASSIGN_DEPARTMENT)) {
            return sameDepartment(actor, task) && sameDepartment(actor, targetUser);
        }
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_ASSIGN_TEAM)) {
            return isTeamTask(actor, task) && isDirectReport(actor, targetUser);
        }
        return false;
    }

    public void assertCanAssign(UserPrincipal actor, Task task, User targetUser) {
        if (!canAssignTask(actor, task, targetUser)) {
            log.warn("Access denied: user {} may not assign task {} to user {}",
                    actor.getId(), task.getId(), targetUser.getId());
            throw new UnauthorizedTaskAccessException("You do not have permission to assign this task.");
        }
    }

    public boolean canViewTask(UserPrincipal actor, Task task) {
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_VIEW_ALL)
                || permissionService.hasPermission(actor, TaskPermissions.TASK_ASSIGN_ALL)) {
            return true;
        }
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_VIEW_DEPARTMENT)
                && sameDepartment(actor, task)) {
            return true;
        }
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_VIEW_TEAM)
                && isTeamTask(actor, task)) {
            return true;
        }
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_VIEW_ASSIGNED)
                && isAssignee(actor, task)) {
            return true;
        }
        return isCreator(actor, task) || isAssignee(actor, task);
    }

    public void assertCanView(UserPrincipal actor, Task task) {
        if (!canViewTask(actor, task)) {
            log.warn("Access denied: user {} may not view task {}", actor.getId(), task.getId());
            throw new UnauthorizedTaskAccessException("You do not have permission to view this task.");
        }
    }

    public boolean canUpdateTask(UserPrincipal actor, Task task) {
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_ASSIGN_ALL)) {
            return true;
        }
        // tasks:update means "update any task you can see", not any task at all.
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_UPDATE) && canViewTask(actor, task)) {
            return true;
        }
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_ASSIGN_DEPARTMENT)
                && sameDepartment(actor, task)) {
            return true;
        }
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_ASSIGN_TEAM)
                && isTeamTask(actor, task)) {
            return true;
        }
        return permissionService.hasPermission(actor, TaskPermissions.TASK_UPDATE_OWN)
                && isAssignee(actor, task);
    }

    public void assertCanUpdate(UserPrincipal actor, Task task) {
        if (!canUpdateTask(actor, task)) {
            log.warn("Access denied: user {} may not update task {}", actor.getId(), task.getId());
            throw new UnauthorizedTaskAccessException("You do not have permission to update this task.");
        }
    }

    public boolean canTransition(UserPrincipal actor, Task task, TaskStatus target) {
        if (target == TaskStatus.CANCELLED) {
            return canCancel(actor, task);
        }
        if (task.getStatus().isTerminal() && target == TaskStatus.TODO) {
            return permissionService.hasPermission(actor, TaskPermissions.TASK_TRANSITION_REOPEN_ALL);
        }
        if (target == TaskStatus.APPROVED || target == TaskStatus.REJECTED) {
            return canReview(actor, task);
        }
        if (target == TaskStatus.DONE) {
            return canClose(actor, task);
        }
        if (target == TaskStatus.IN_PROGRESS || target == TaskStatus.IN_REVIEW || target == TaskStatus.BLOCKED) {
            return permissionService.hasPermission(actor, TaskPermissions.TASK_TRANSITION_ADVANCE_OWN)
                    && isAssignee(actor, task);
        }
        return false;
    }

    public boolean canReview(UserPrincipal actor, Task task) {
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_TRANSITION_REVIEW_ALL)
                || permissionService.hasPermission(actor, TaskPermissions.TASK_ASSIGN_ALL)) {
            return true;
        }
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_TRANSITION_REVIEW_DEPARTMENT)
                && sameDepartment(actor, task)) {
            return true;
        }
        return permissionService.hasPermission(actor, TaskPermissions.TASK_TRANSITION_REVIEW_TEAM)
                && isDirectReportTask(actor, task);
    }

    /** Reviewer may review tasks in every department (same rule as the all-scope branch of canReview). */
    public boolean canReviewAll(UserPrincipal actor) {
        return permissionService.hasPermission(actor, TaskPermissions.TASK_TRANSITION_REVIEW_ALL)
                || permissionService.hasPermission(actor, TaskPermissions.TASK_ASSIGN_ALL);
    }

    /** Reviewer may review tasks in their own department only. */
    public boolean canReviewDepartment(UserPrincipal actor) {
        return actor.getDepartmentId() != null
                && permissionService.hasPermission(actor, TaskPermissions.TASK_TRANSITION_REVIEW_DEPARTMENT);
    }

    /** Reviewer may review their direct reports' tasks only. */
    public boolean canReviewTeam(UserPrincipal actor) {
        return permissionService.hasPermission(actor, TaskPermissions.TASK_TRANSITION_REVIEW_TEAM);
    }

    /** Archiving (soft delete) needs tasks:delete and is limited to tasks the actor can see. */
    public boolean canArchive(UserPrincipal actor, Task task) {
        return permissionService.hasPermission(actor, TaskPermissions.TASK_DELETE) && canViewTask(actor, task);
    }

    public void assertCanArchive(UserPrincipal actor, Task task) {
        if (!canArchive(actor, task)) {
            log.warn("Access denied: user {} may not archive task {}", actor.getId(), task.getId());
            throw new UnauthorizedTaskAccessException("You do not have permission to delete this task.");
        }
    }

    public boolean canClose(UserPrincipal actor, Task task) {
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_TRANSITION_CLOSE_ALL)) {
            return true;
        }
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_TRANSITION_CLOSE_DEPARTMENT)
                && sameDepartment(actor, task)) {
            return true;
        }
        return permissionService.hasPermission(actor, TaskPermissions.TASK_TRANSITION_CLOSE_TEAM)
                && isTeamTask(actor, task);
    }

    public boolean canCancel(UserPrincipal actor, Task task) {
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_TRANSITION_CANCEL_ALL)) {
            return true;
        }
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_TRANSITION_CANCEL_DEPARTMENT)
                && sameDepartment(actor, task)) {
            return true;
        }
        // No fallback to update rights: an assignee with tasks:update:own must not be able to cancel (Q-10).
        return permissionService.hasPermission(actor, TaskPermissions.TASK_TRANSITION_CANCEL_TEAM)
                && isTeamTask(actor, task);
    }

    /**
     * Title, priority and due date are set by whoever manages the task. An assignee whose only
     * update right is tasks:update:own may change the description only (Q-09).
     */
    public boolean canEditPlanningFields(UserPrincipal actor, Task task) {
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_ASSIGN_ALL)) {
            return true;
        }
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_UPDATE) && canViewTask(actor, task)) {
            return true;
        }
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_ASSIGN_DEPARTMENT)
                && sameDepartment(actor, task)) {
            return true;
        }
        return permissionService.hasPermission(actor, TaskPermissions.TASK_ASSIGN_TEAM)
                && isTeamTask(actor, task);
    }

    public ViewScope resolveViewScope(UserPrincipal actor) {
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_VIEW_ALL)
                || permissionService.hasPermission(actor, TaskPermissions.TASK_ASSIGN_ALL)) {
            log.debug("View scope for user {}: ALL", actor.getId());
            return ViewScope.ALL;
        }
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_VIEW_DEPARTMENT)
                || permissionService.hasPermission(actor, TaskPermissions.TASK_ASSIGN_DEPARTMENT)) {
            log.debug("View scope for user {}: DEPARTMENT", actor.getId());
            return ViewScope.DEPARTMENT;
        }
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_VIEW_TEAM)
                || permissionService.hasPermission(actor, TaskPermissions.TASK_ASSIGN_TEAM)) {
            log.debug("View scope for user {}: TEAM", actor.getId());
            return ViewScope.TEAM;
        }
        log.debug("View scope for user {}: OWN", actor.getId());
        return ViewScope.OWN;
    }

    public boolean isAssignee(UserPrincipal actor, Task task) {
        return task.isAssignee(actor.getId());
    }

    private boolean isCreator(UserPrincipal actor, Task task) {
        return task.getCreator() != null && Objects.equals(task.getCreator().getId(), actor.getId());
    }

    /** Created by the actor, assigned to the actor, or assigned to one of the actor's direct reports. */
    private boolean isTeamTask(UserPrincipal actor, Task task) {
        return isCreator(actor, task)
                || (task.getAssignee() != null && Objects.equals(task.getAssignee().getId(), actor.getId()))
                || isDirectReportTask(actor, task);
    }

    private boolean isDirectReportTask(UserPrincipal actor, Task task) {
        return task.getAssignee() != null && isDirectReport(actor, task.getAssignee());
    }

    private boolean isDirectReport(UserPrincipal actor, User user) {
        return user.getManager() != null && Objects.equals(user.getManager().getId(), actor.getId());
    }

    private boolean sameDepartment(UserPrincipal actor, Task task) {
        return actor.getDepartmentId() != null
                && task.getDepartment() != null
                && Objects.equals(actor.getDepartmentId(), task.getDepartment().getId());
    }

    private boolean sameDepartment(UserPrincipal actor, User target) {
        return actor.getDepartmentId() != null
                && target.getDepartment() != null
                && Objects.equals(actor.getDepartmentId(), target.getDepartment().getId());
    }
}
