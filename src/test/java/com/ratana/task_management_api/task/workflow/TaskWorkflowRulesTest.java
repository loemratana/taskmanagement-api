package com.ratana.task_management_api.task.workflow;

import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.task.exception.InvalidTaskTransitionException;
import com.ratana.task_management_api.task.exception.UnauthorizedTaskTransitionException;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.task.service.TaskAuthorizationService;
import com.ratana.task_management_api.task.service.TaskPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real authorization + workflow classes against the seeded role grants (MIG/V7, V10). */
class TaskWorkflowRulesTest {

    private TaskAuthorizationService authorization;
    private TaskWorkflowService workflow;

    private final UUID departmentId = UUID.randomUUID();
    private UserPrincipal admin;
    private UserPrincipal manager;
    private UserPrincipal employee;
    private Task task;

    private UserPrincipal principal(String... authorities) {
        return new UserPrincipal(UUID.randomUUID(), "x@example.com", "hash", departmentId,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList(), true);
    }

    @BeforeEach
    void setUp() {
        authorization = new TaskAuthorizationService(new TaskPermissionService());
        workflow = new TaskWorkflowService(new TaskStatusTransitionPolicy(authorization));

        admin = principal("ROLE_ADMIN", "tasks:assign:all", "tasks:update", "tasks:view:all",
                "tasks:transition:review:all", "tasks:transition:close:all", "tasks:transition:cancel:all",
                "tasks:transition:reopen:all", "tasks:transition:advance:own");
        manager = principal("ROLE_MANAGER", "tasks:create", "tasks:update", "tasks:view:team", "tasks:assign:team",
                "tasks:transition:advance:own", "tasks:transition:review:team", "tasks:transition:close:team",
                "tasks:transition:cancel:team");
        employee = principal("ROLE_EMPLOYEE", "tasks:read", "tasks:view:assigned", "tasks:update:own",
                "tasks:transition:advance:own");

        User managerUser = new User();
        managerUser.setId(manager.getId());
        User employeeUser = new User();
        employeeUser.setId(employee.getId());
        employeeUser.setManager(managerUser);
        Department department = new Department();
        department.setId(departmentId);

        task = Task.builder().id(UUID.randomUUID()).status(TaskStatus.TODO)
                .department(department).creator(managerUser).assignee(employeeUser).build();
    }

    @Test
    @DisplayName("TC-WF-09: an assignee with only update:own cannot cancel (DEF-09)")
    void employee_cannotCancelOwnTask() {
        assertThat(authorization.canUpdateTask(employee, task)).isTrue();
        assertThat(authorization.canCancel(employee, task)).isFalse();
        assertThatThrownBy(() -> workflow.assertCanChangeStatus(task, TaskStatus.CANCELLED, employee))
                .isInstanceOf(UnauthorizedTaskTransitionException.class);
        assertThat(authorization.canCancel(manager, task)).isTrue();
    }

    @Test
    @DisplayName("TC-WF-12: admins follow the status graph (DEF-08)")
    void admin_cannotLeaveGraph() {
        task.setStatus(TaskStatus.DONE);
        assertThatThrownBy(() -> workflow.assertCanChangeStatus(task, TaskStatus.IN_PROGRESS, admin))
                .isInstanceOf(InvalidTaskTransitionException.class);
        task.setStatus(TaskStatus.TODO);
        assertThatThrownBy(() -> workflow.assertCanChangeStatus(task, TaskStatus.DONE, admin))
                .isInstanceOf(InvalidTaskTransitionException.class);
    }

    @Test
    @DisplayName("TC-WF-13: reopen DONE/CANCELLED → TODO needs tasks:transition:reopen:all (Q-07)")
    void reopen_requiresReopenPermission() {
        task.setStatus(TaskStatus.DONE);
        assertThatCode(() -> workflow.assertCanChangeStatus(task, TaskStatus.TODO, admin)).doesNotThrowAnyException();
        assertThatThrownBy(() -> workflow.assertCanChangeStatus(task, TaskStatus.TODO, manager))
                .isInstanceOf(UnauthorizedTaskTransitionException.class);

        task.setStatus(TaskStatus.CANCELLED);
        assertThatCode(() -> workflow.assertCanChangeStatus(task, TaskStatus.TODO, admin)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Assignee can block an in-progress task and resume it; blocked work can't skip to review")
    void blocked_assigneeBlocksAndResumes() {
        task.setStatus(TaskStatus.IN_PROGRESS);
        assertThatCode(() -> workflow.assertCanChangeStatus(task, TaskStatus.BLOCKED, employee))
                .doesNotThrowAnyException();

        task.setStatus(TaskStatus.BLOCKED);
        assertThatCode(() -> workflow.assertCanChangeStatus(task, TaskStatus.IN_PROGRESS, employee))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> workflow.assertCanChangeStatus(task, TaskStatus.IN_REVIEW, employee))
                .isInstanceOf(InvalidTaskTransitionException.class);
        assertThatThrownBy(() -> workflow.assertCanChangeStatus(task, TaskStatus.CANCELLED, employee))
                .isInstanceOf(UnauthorizedTaskTransitionException.class);
        assertThatCode(() -> workflow.assertCanChangeStatus(task, TaskStatus.CANCELLED, manager))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Only the assignee can mark a task blocked, and only from IN_PROGRESS")
    void blocked_onlyAssigneeFromInProgress() {
        task.setStatus(TaskStatus.IN_PROGRESS);
        assertThatThrownBy(() -> workflow.assertCanChangeStatus(task, TaskStatus.BLOCKED, manager))
                .isInstanceOf(UnauthorizedTaskTransitionException.class);

        task.setStatus(TaskStatus.TODO);
        assertThatThrownBy(() -> workflow.assertCanChangeStatus(task, TaskStatus.BLOCKED, employee))
                .isInstanceOf(InvalidTaskTransitionException.class);
    }

    @Test
    @DisplayName("TC-TASK-10: only the task's manager may change planning fields (Q-09)")
    void planningFields_managerYesAssigneeNo() {
        assertThat(authorization.canEditPlanningFields(manager, task)).isTrue();
        assertThat(authorization.canEditPlanningFields(admin, task)).isTrue();
        assertThat(authorization.canEditPlanningFields(employee, task)).isFalse();
    }
}
