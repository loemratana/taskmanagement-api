package com.ratana.task_management_api.task.service;

import com.ratana.task_management_api.task.dto.request.AssignTaskRequest;
import com.ratana.task_management_api.task.dto.request.UpdateTaskRequest;
import com.ratana.task_management_api.task.dto.response.TaskResponse;
import com.ratana.task_management_api.task.enums.Priority;
import com.ratana.task_management_api.task.enums.SubmissionStatus;
import com.ratana.task_management_api.task.enums.TaskAuditAction;
import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.user.enums.UserStatus;
import com.ratana.task_management_api.common.exception.BadRequestException;
import com.ratana.task_management_api.task.exception.InvalidAssignmentException;
import com.ratana.task_management_api.task.exception.TaskStateConflictException;
import com.ratana.task_management_api.task.exception.UnauthorizedTaskAccessException;
import com.ratana.task_management_api.task.mapper.TaskMapper;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.task.repository.TaskRepository;
import com.ratana.task_management_api.task.repository.TaskSubmissionRepository;
import com.ratana.task_management_api.user.repository.UserRepository;
import com.ratana.task_management_api.infrastructure.security.SecurityUtils;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.task.workflow.TaskWorkflowService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Phase 1 rules in TaskServiceImpl: DEF-10, DEF-08 override, Q-07 reopen, DEF-17/Q-09, DEF-18, DEF-12 audit. */
@ExtendWith(MockitoExtension.class)
class TaskServicePhase1RulesTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TaskMapper taskMapper;
    @Mock private TaskAuthorizationService taskAuthorizationService;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TaskWorkflowService taskWorkflowService;
    @Mock private TaskSubmissionRepository taskSubmissionRepository;
    @Mock private TaskAuditService taskAuditService;
    @Mock private TaskGroupService taskGroupService;
    @InjectMocks private TaskServiceImpl taskService;

    private MockedStatic<SecurityUtils> securityUtils;
    private UserPrincipal actor;
    private UUID actorId;
    private UUID taskId;
    private Task task;

    @BeforeEach
    void setUp() {
        actor = mock(UserPrincipal.class);
        actorId = UUID.randomUUID();
        lenient().when(actor.getId()).thenReturn(actorId);
        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUser).thenReturn(actor);

        taskId = UUID.randomUUID();
        task = Task.builder().id(taskId).title("T").description("D").priority(Priority.MEDIUM)
                .status(TaskStatus.IN_PROGRESS).build();
        lenient().when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        lenient().when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(taskMapper.toResponse(any(Task.class))).thenReturn(new TaskResponse());
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    @Test
    @DisplayName("TC-WF-10: PATCH to IN_REVIEW is refused — use the submission endpoint (DEF-10)")
    void patchToInReview_conflict() {
        assertThatThrownBy(() -> taskService.changeStatus(taskId, "IN_REVIEW", null))
                .isInstanceOf(TaskStateConflictException.class)
                .extracting("errorCode").isEqualTo(TaskStateConflictException.USE_SUBMISSION_ENDPOINT);
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-WF-05/11: PATCH to APPROVED or REJECTED is refused — use the review endpoint")
    void patchToApproved_conflict() {
        task.setStatus(TaskStatus.IN_REVIEW);
        assertThatThrownBy(() -> taskService.changeStatus(taskId, "APPROVED", null))
                .isInstanceOf(TaskStateConflictException.class)
                .extracting("errorCode").isEqualTo(TaskStateConflictException.USE_REVIEW_ENDPOINT);
        assertThatThrownBy(() -> taskService.changeStatus(taskId, "REJECTED", null))
                .isInstanceOf(TaskStateConflictException.class);
    }

    @Test
    @DisplayName("TC-WF-13: reopening needs a reason and is audited as TASK_REOPENED (Q-07)")
    void reopen_requiresReasonAndAudits() {
        task.setStatus(TaskStatus.DONE);
        assertThatThrownBy(() -> taskService.changeStatus(taskId, "TODO", "  "))
                .isInstanceOf(BadRequestException.class);

        taskService.changeStatus(taskId, "TODO", "Customer reported a defect");

        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
        verify(taskAuditService).record(eq(task), eq(actorId), eq(TaskAuditAction.TASK_REOPENED),
                any(), any(), eq("Customer reported a defect"));
    }

    @Test
    @DisplayName("normal status change is audited as STATUS_CHANGED (DEF-12)")
    void statusChange_isAudited() {
        taskService.changeStatus(taskId, "CANCELLED", null);
        verify(taskAuditService).record(eq(task), eq(actorId), eq(TaskAuditAction.STATUS_CHANGED),
                any(), any(), eq(null));
    }

    @Test
    @DisplayName("TC-WF-12: admin override needs a reason, is refused while a submission is pending, and is audited")
    void override_rules() {
        assertThatThrownBy(() -> taskService.overrideStatus(taskId, "DONE", null))
                .isInstanceOf(BadRequestException.class);

        when(taskSubmissionRepository.existsByTaskIdAndStatus(taskId, SubmissionStatus.PENDING)).thenReturn(true);
        assertThatThrownBy(() -> taskService.overrideStatus(taskId, "DONE", "fix data"))
                .isInstanceOf(TaskStateConflictException.class);

        when(taskSubmissionRepository.existsByTaskIdAndStatus(taskId, SubmissionStatus.PENDING)).thenReturn(false);
        taskService.overrideStatus(taskId, "DONE", "fix data");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
        verify(taskAuditService).record(eq(task), eq(actorId), eq(TaskAuditAction.STATUS_OVERRIDDEN),
                any(), any(), eq("fix data"));
        verifyNoInteractions(taskWorkflowService);
    }

    @Test
    @DisplayName("TC-TASK-12: a finished task cannot be edited (DEF-17)")
    void update_terminalTask_conflict() {
        task.setStatus(TaskStatus.DONE);
        assertThatThrownBy(() -> taskService.updateOwnTask(taskId, update("T", "new", Priority.MEDIUM, null)))
                .isInstanceOf(TaskStateConflictException.class)
                .extracting("errorCode").isEqualTo(TaskStateConflictException.TASK_NOT_EDITABLE);
    }

    @Test
    @DisplayName("TC-TASK-10: an assignee may change only the description (Q-09)")
    void update_assigneeLimitedToDescription() {
        when(taskAuthorizationService.canEditPlanningFields(actor, task)).thenReturn(false);

        assertThatThrownBy(() -> taskService.updateOwnTask(taskId,
                update("T", "D", Priority.MEDIUM, LocalDate.now().plusDays(30))))
                .isInstanceOf(UnauthorizedTaskAccessException.class);

        taskService.updateOwnTask(taskId, update("T", "Better description", Priority.MEDIUM, null));
        assertThat(task.getDescription()).isEqualTo("Better description");
        verify(taskAuditService).record(eq(task), any(), eq(TaskAuditAction.TASK_UPDATED), any(), any(), eq(null));
    }

    @Test
    @DisplayName("TC-ASSIGN-05/06: no assigning inactive users or finished tasks (DEF-18)")
    void assign_inactiveUserOrTerminalTask_rejected() {
        User inactive = new User();
        inactive.setId(UUID.randomUUID());
        inactive.setStatus(UserStatus.INACTIVE);
        when(userRepository.findById(inactive.getId())).thenReturn(Optional.of(inactive));
        AssignTaskRequest request = new AssignTaskRequest();
        request.setAssigneeId(inactive.getId());

        assertThatThrownBy(() -> taskService.assignTask(taskId, request))
                .isInstanceOf(InvalidAssignmentException.class);

        task.setStatus(TaskStatus.CANCELLED);
        assertThatThrownBy(() -> taskService.assignTask(taskId, request))
                .isInstanceOf(TaskStateConflictException.class)
                .extracting("errorCode").isEqualTo(TaskStateConflictException.TASK_TERMINAL);
    }

    private static UpdateTaskRequest update(String title, String description, Priority priority, LocalDate dueDate) {
        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setTitle(title);
        request.setDescription(description);
        request.setPriority(priority);
        request.setDueDate(dueDate);
        return request;
    }
}
