package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.enums.TaskStatus;
import com.ratana.task_management_api.enums.ViewScope;
import com.ratana.task_management_api.exception.UnauthorizedTaskAccessException;
import com.ratana.task_management_api.model.Task;
import com.ratana.task_management_api.model.User;
import com.ratana.task_management_api.security.UserPrincipal;
import com.ratana.task_management_api.workflow.TaskPermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * Single gate for resource-scoped task authorization.
 * Controllers may use coarse {@code @PreAuthorize}; this service owns dept/assignee rules.
 */
@Service
@RequiredArgsConstructor
public class TaskAuthorizationService {

    private final TaskPermissionService permissionService;

    public boolean canCreateTask(UserPrincipal actor) {
        return permissionService.hasPermission(actor, TaskPermissions.TASK_CREATE);
    }

    public void assertCanCreate(UserPrincipal actor) {
        if (!canCreateTask(actor)) {
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
        return false;
    }

    public void assertCanAssign(UserPrincipal actor, Task task, User targetUser) {
        if (!canAssignTask(actor, task, targetUser)) {
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
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_VIEW_ASSIGNED)
                && isAssignee(actor, task)) {
            return true;
        }
        return isCreator(actor, task) || isAssignee(actor, task);
    }

    public void assertCanView(UserPrincipal actor, Task task) {
        if (!canViewTask(actor, task)) {
            throw new UnauthorizedTaskAccessException("You do not have permission to view this task.");
        }
    }

    public boolean canUpdateTask(UserPrincipal actor, Task task) {
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_ASSIGN_ALL)
                || permissionService.hasPermission(actor, TaskPermissions.TASK_UPDATE)) {
            return true;
        }
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_ASSIGN_DEPARTMENT)
                && sameDepartment(actor, task)) {
            return true;
        }
        return permissionService.hasPermission(actor, TaskPermissions.TASK_UPDATE_OWN)
                && isAssignee(actor, task);
    }

    public void assertCanUpdate(UserPrincipal actor, Task task) {
        if (!canUpdateTask(actor, task)) {
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
        if (target == TaskStatus.IN_PROGRESS || target == TaskStatus.IN_REVIEW) {
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
        return permissionService.hasPermission(actor, TaskPermissions.TASK_TRANSITION_REVIEW_DEPARTMENT)
                && sameDepartment(actor, task);
    }

    public boolean canClose(UserPrincipal actor, Task task) {
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_TRANSITION_CLOSE_ALL)) {
            return true;
        }
        return permissionService.hasPermission(actor, TaskPermissions.TASK_TRANSITION_CLOSE_DEPARTMENT)
                && sameDepartment(actor, task);
    }

    public boolean canCancel(UserPrincipal actor, Task task) {
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_TRANSITION_CANCEL_ALL)) {
            return true;
        }
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_TRANSITION_CANCEL_DEPARTMENT)
                && sameDepartment(actor, task)) {
            return true;
        }
        return canUpdateTask(actor, task);
    }

    public ViewScope resolveViewScope(UserPrincipal actor) {
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_VIEW_ALL)
                || permissionService.hasPermission(actor, TaskPermissions.TASK_ASSIGN_ALL)) {
            return ViewScope.ALL;
        }
        if (permissionService.hasPermission(actor, TaskPermissions.TASK_VIEW_DEPARTMENT)
                || permissionService.hasPermission(actor, TaskPermissions.TASK_ASSIGN_DEPARTMENT)) {
            return ViewScope.DEPARTMENT;
        }
        return ViewScope.OWN;
    }

    public boolean isAssignee(UserPrincipal actor, Task task) {
        return task.isAssignee(actor.getId());
    }

    private boolean isCreator(UserPrincipal actor, Task task) {
        return task.getCreator() != null && Objects.equals(task.getCreator().getId(), actor.getId());
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
