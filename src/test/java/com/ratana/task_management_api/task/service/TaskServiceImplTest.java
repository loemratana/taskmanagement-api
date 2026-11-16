package com.ratana.task_management_api.task.service;

import com.ratana.task_management_api.task.dto.request.AssignTaskRequest;
import com.ratana.task_management_api.task.dto.request.CreateTaskRequest;
import com.ratana.task_management_api.task.dto.request.UpdateTaskRequest;
import com.ratana.task_management_api.task.dto.response.TaskResponse;
import com.ratana.task_management_api.task.enums.Priority;
import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.task.event.TaskEvent;
import com.ratana.task_management_api.task.event.TaskEventType;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.task.exception.TaskNotFoundException;
import com.ratana.task_management_api.task.exception.UnauthorizedTaskAccessException;
import com.ratana.task_management_api.task.mapper.TaskMapper;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.organization.repository.DepartmentRepository;
import com.ratana.task_management_api.task.repository.TaskRepository;
import com.ratana.task_management_api.task.repository.TaskSubmissionRepository;
import com.ratana.task_management_api.task.enums.SubmissionStatus;
import com.ratana.task_management_api.task.exception.TaskStateConflictException;
import com.ratana.task_management_api.common.exception.BadRequestException;
import com.ratana.task_management_api.user.repository.UserRepository;
import com.ratana.task_management_api.infrastructure.security.SecurityUtils;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.task.workflow.TaskWorkflowService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TaskServiceImplTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TaskMapper taskMapper;
    @Mock private TaskAuthorizationService taskAuthorizationService;
    @Mock private UserRepository userRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TaskWorkflowService taskWorkflowService;
    @Mock private TaskAuditService taskAuditService;
    @Mock private TaskGroupService taskGroupService;
    @Mock private TaskSubmissionRepository taskSubmissionRepository;

    @InjectMocks
    private TaskServiceImpl taskService;

    private MockedStatic<SecurityUtils> securityUtilsMock;
    private UserPrincipal actor;
    private UUID actorId;
    private UUID taskId;
    private UUID departmentId;
    private UUID assigneeId;
    private CreateTaskRequest request;
    private TaskResponse response;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        taskId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        assigneeId = UUID.randomUUID();

        actor = mock(UserPrincipal.class);
        lenient().when(actor.getId()).thenReturn(actorId);
        lenient().when(actor.getUsername()).thenReturn("john.doe");

        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(actor);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Test
    @DisplayName("createTask – success without assignee")
    void createTask_success_noAssignee() {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle("New Task");
        request.setDescription("Desc");
        request.setDepartmentId(departmentId);

        User creator = new User();
        creator.setId(actorId);

        Department department = new Department();
        department.setId(departmentId);

        Task taskEntity = new Task();
        Task savedTask = new Task();
        savedTask.setId(taskId);
        savedTask.setStatus(TaskStatus.TODO);

        TaskResponse response = new TaskResponse();
        response.setId(taskId);

        doNothing().when(taskAuthorizationService).assertCanCreate(actor);
        when(taskMapper.toEntity(request)).thenReturn(taskEntity);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(creator));
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);
        when(taskMapper.toResponse(savedTask)).thenReturn(response);

        TaskResponse result = taskService.createTask(request, actor);

        assertThat(result.getId()).isEqualTo(taskId);
        verify(taskRepository).save(taskEntity);
        verify(eventPublisher, never()).publishEvent(any());
        assertThat(taskEntity.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(taskEntity.getCreator()).isEqualTo(creator);
        assertThat(taskEntity.getDepartment()).isEqualTo(department);
    }


    @Test
    @DisplayName("createTask – success with assignee")
    void createTask_success_withAssignee() {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle("New Task");
        request.setDepartmentId(departmentId);
        request.setAssigneeId(assigneeId);

        User creator = new User();
        creator.setId(actorId);
        creator.setFullName("John Doe");
        Department department = new Department();
        department.setId(departmentId);
        User assignee = new User();
        assignee.setId(assigneeId);

        Task taskEntity = new Task();
        Task savedTask = new Task();
        savedTask.setId(taskId);
        savedTask.setTitle("New Task");
        savedTask.setAssignee(assignee);
        savedTask.setDepartment(department);

        doNothing().when(taskAuthorizationService).assertCanCreate(actor);
        doNothing().when(taskAuthorizationService).assertCanAssign(eq(actor), any(Task.class), eq(assignee));
        when(taskMapper.toEntity(request)).thenReturn(taskEntity);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(creator));
        when(userRepository.findById(assigneeId)).thenReturn(Optional.of(assignee));
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);
        when(taskMapper.toResponse(savedTask)).thenReturn(new TaskResponse());

        taskService.createTask(request, actor);

        verify(taskAuthorizationService).assertCanAssign(eq(actor), any(Task.class), eq(assignee));
        verify(eventPublisher).publishEvent(any(TaskEvent.class));
        assertThat(taskEntity.getAssignee()).isEqualTo(assignee);
    }

    @Test
    @DisplayName("createTask – not authorized")
    void createTask_notAuthorized() {
        doThrow(new UnauthorizedTaskAccessException("Not authorized to create tasks."))
                .when(taskAuthorizationService).assertCanCreate(actor);

        assertThatThrownBy(() -> taskService.createTask(new CreateTaskRequest(), actor))
                .isInstanceOf(UnauthorizedTaskAccessException.class)
                .hasMessageContaining("Not authorized");
    }

    @Test
    @DisplayName("createTask – department not found")
    void createTask_departmentNotFound() {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setDepartmentId(departmentId);

        doNothing().when(taskAuthorizationService).assertCanCreate(actor);
        when(taskMapper.toEntity(request)).thenReturn(new Task());
        when(userRepository.findById(actorId)).thenReturn(Optional.of(new User()));
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.createTask(request, actor))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Department not found");
    }

    @Test
    @DisplayName("assignTask – task not found")
    void assignTask_taskNotFound() {
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.assignTask(taskId, new AssignTaskRequest()))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("Task not found");
    }

    @Test
    @DisplayName("assignTask – assignee not found")
    void assignTask_assigneeNotFound() {
        Task task = new Task();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(userRepository.findById(assigneeId)).thenReturn(Optional.empty());

        AssignTaskRequest request = new AssignTaskRequest();
        request.setAssigneeId(assigneeId);

        assertThatThrownBy(() -> taskService.assignTask(taskId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Assignee not found");
    }

    @Test
    @DisplayName("assignTask – success")
    void assignTask_success() {
        AssignTaskRequest request = new AssignTaskRequest();
        request.setAssigneeId(assigneeId);

        Task task = new Task();
        task.setId(taskId);
        task.setTitle("Important Task");

        Department department = new Department();
        department.setId(departmentId);
        task.setDepartment(department);

        User assignee = new User();
        assignee.setId(assigneeId);

        Task saved = new Task();
        saved.setId(taskId);
        saved.setTitle("Important Task");
        saved.setAssignee(assignee);
        saved.setDepartment(department);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(userRepository.findById(assigneeId)).thenReturn(Optional.of(assignee));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(new User()));
        doNothing().when(taskAuthorizationService).assertCanAssign(actor, task, assignee);
        when(taskRepository.save(task)).thenReturn(saved);
        when(taskMapper.toResponse(saved)).thenReturn(new TaskResponse());

        taskService.assignTask(taskId, request);

        assertThat(task.getAssignee()).isEqualTo(assignee);
        ArgumentCaptor<TaskEvent> eventCaptor = ArgumentCaptor.forClass(TaskEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo(TaskEventType.ASSIGNED);
        assertThat(eventCaptor.getValue().assigneeId()).isEqualTo(assigneeId);
    }

    @Test
    @DisplayName("assignTask – refused while a submission is waiting for review")
    void assignTask_pendingSubmission_conflict() {
        AssignTaskRequest request = new AssignTaskRequest();
        request.setAssigneeId(assigneeId);
        Task task = new Task();
        task.setId(taskId);
        task.setStatus(TaskStatus.IN_REVIEW);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(userRepository.findById(assigneeId)).thenReturn(Optional.of(new User()));
        when(taskSubmissionRepository.existsByTaskIdAndStatus(taskId, SubmissionStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> taskService.assignTask(taskId, request))
                .isInstanceOf(TaskStateConflictException.class);
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeStatus – blocking a task requires a reason")
    void changeStatus_blockedWithoutReason_badRequest() {
        Task task = new Task();
        task.setId(taskId);
        task.setStatus(TaskStatus.IN_PROGRESS);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskService.changeStatus(taskId, "BLOCKED", "  "))
                .isInstanceOf(BadRequestException.class);
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateOwnTask – success")
    void updateOwnTask_success() {
        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setTitle("Updated Title");
        request.setDescription("Updated Desc");
        request.setPriority(Priority.HIGH);
        request.setDueDate(LocalDate.now().plusDays(3));

        Task task = mock(Task.class);
        when(task.isActive()).thenReturn(true); // archived tasks are treated as not found
        when(task.getStatus()).thenReturn(TaskStatus.IN_PROGRESS);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        doNothing().when(taskAuthorizationService).assertCanUpdate(actor, task);
        // Title/priority/due date change → the actor must manage the task, not just be its assignee (Q-09).
        when(taskAuthorizationService.canEditPlanningFields(actor, task)).thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(taskMapper.toResponse(task)).thenReturn(new TaskResponse());

        taskService.updateOwnTask(taskId, request);

        verify(taskAuthorizationService).assertCanUpdate(actor, task);
        verify(task).setTitle("Updated Title");
        verify(taskRepository).save(task);
    }

    @Test
    @DisplayName("updateOwnTask – not authorized")
    void updateOwnTask_notAuthorized() {
        Task task = mock(Task.class);
        when(task.isActive()).thenReturn(true);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        doThrow(new UnauthorizedTaskAccessException("You do not have permission to update this task."))
                .when(taskAuthorizationService).assertCanUpdate(actor, task);

        assertThatThrownBy(() -> taskService.updateOwnTask(taskId, new UpdateTaskRequest()))
                .isInstanceOf(UnauthorizedTaskAccessException.class);
    }

    @Test
    @DisplayName("changeStatus – success and publishes event")
    void changeStatus_success() {
        Task task = new Task();
        task.setId(taskId);
        task.setTitle("T");
        task.setStatus(TaskStatus.TODO);
        User assignee = new User();
        assignee.setId(assigneeId);
        task.setAssignee(assignee);

        Task saved = new Task();
        saved.setId(taskId);
        saved.setTitle("T");
        saved.setStatus(TaskStatus.IN_PROGRESS);
        saved.setAssignee(assignee);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        doNothing().when(taskWorkflowService)
                .assertCanChangeStatus(task, TaskStatus.IN_PROGRESS, actor);
        when(taskRepository.save(task)).thenReturn(saved);
        when(taskMapper.toResponse(saved)).thenReturn(new TaskResponse());

        taskService.changeStatus(taskId, "IN_PROGRESS");

        verify(taskWorkflowService).assertCanChangeStatus(task, TaskStatus.IN_PROGRESS, actor);
        verify(eventPublisher).publishEvent(any(TaskEvent.class));
    }

    @Test
    @DisplayName("getTask – success")
    void getTask_success() {
        Task task = new Task();
        task.setId(taskId);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        doNothing().when(taskAuthorizationService).assertCanView(actor, task);
        when(taskMapper.toResponse(task)).thenReturn(new TaskResponse());

        taskService.getTask(taskId, actor);

        verify(taskAuthorizationService).assertCanView(actor, task);
    }

    @Test
    @DisplayName("getTask – not found")
    void getTask_notFound() {
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTask(taskId, actor))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    @DisplayName("archiveTask – soft-deletes the task and its active subtasks")
    void archiveTask_success() {
        Task subtask = new Task();
        Task task = new Task();
        task.getSubtasks().add(subtask);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        taskService.archiveTask(taskId, actor);

        assertThat(task.isActive()).isFalse();
        assertThat(subtask.isActive()).isFalse();
        verify(taskAuthorizationService).assertCanArchive(actor, task);
        verify(taskRepository).save(task);
        verify(taskAuditService).record(eq(task), eq(actorId),
                eq(com.ratana.task_management_api.task.enums.TaskAuditAction.TASK_ARCHIVED), anyMap());
    }

    @Test
    @DisplayName("archiveTask – denied leaves the task active")
    void archiveTask_denied() {
        Task task = new Task();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        doThrow(new UnauthorizedTaskAccessException("nope"))
                .when(taskAuthorizationService).assertCanArchive(actor, task);

        assertThatThrownBy(() -> taskService.archiveTask(taskId, actor))
                .isInstanceOf(UnauthorizedTaskAccessException.class);
        assertThat(task.isActive()).isTrue();
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("getTask – archived task is treated as not found")
    void getTask_archivedIsNotFound() {
        Task task = new Task();
        task.setActive(false);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskService.getTask(taskId, actor))
                .isInstanceOf(TaskNotFoundException.class);
    }
}
