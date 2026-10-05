package com.ratana.task_management_api.services;

import com.ratana.task_management_api.dto.request.AssignTaskRequest;
import com.ratana.task_management_api.dto.request.CreateTaskRequest;
import com.ratana.task_management_api.dto.request.ReviewTaskRequest;
import com.ratana.task_management_api.dto.request.UpdateTaskRequest;
import com.ratana.task_management_api.dto.response.TaskResponse;
import com.ratana.task_management_api.dto.response.TaskReviewResponse;
import com.ratana.task_management_api.enums.Priority;
import com.ratana.task_management_api.enums.TaskStatus;
import com.ratana.task_management_api.event.TaskAssignedEvent;
import com.ratana.task_management_api.event.TaskStatusChangedEvent;
import com.ratana.task_management_api.exception.BadRequestException;
import com.ratana.task_management_api.exception.ResourceNotFoundException;
import com.ratana.task_management_api.exception.TaskNotFoundException;
import com.ratana.task_management_api.exception.UnauthorizedTaskAccessException;
import com.ratana.task_management_api.mapper.TaskMapper;
import com.ratana.task_management_api.mapper.TaskReviewMapper;
import com.ratana.task_management_api.model.Department;
import com.ratana.task_management_api.model.Task;
import com.ratana.task_management_api.model.TaskReview;
import com.ratana.task_management_api.model.User;
import com.ratana.task_management_api.repository.DepartmentRepository;
import com.ratana.task_management_api.repository.TaskRepository;
import com.ratana.task_management_api.repository.TaskReviewRepository;
import com.ratana.task_management_api.repository.UserRepository;
import com.ratana.task_management_api.security.SecurityUtils;
import com.ratana.task_management_api.security.UserPrincipal;
import com.ratana.task_management_api.service.ServiceImpl.TaskAuthorizationService;
import com.ratana.task_management_api.service.ServiceImpl.TaskServiceImpl;
import com.ratana.task_management_api.service.ServiceImpl.TaskWorkflowService;
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
    @Mock private TaskReviewRepository taskReviewRepository;
    @Mock private TaskReviewMapper taskReviewMapper;

    @InjectMocks
    private TaskServiceImpl taskService;

    private MockedStatic<SecurityUtils> securityUtilsMock;
    private UserPrincipal actor;
    private UUID actorId;
    private UUID taskId;
    private UUID departmentId;
    private UUID assigneeId;

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
        verify(eventPublisher).publishEvent(any(TaskAssignedEvent.class));
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
        ArgumentCaptor<TaskAssignedEvent> eventCaptor = ArgumentCaptor.forClass(TaskAssignedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getAssigneeId()).isEqualTo(assigneeId);
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
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        doNothing().when(taskAuthorizationService).assertCanUpdate(actor, task);
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
        verify(eventPublisher).publishEvent(any(TaskStatusChangedEvent.class));
    }

    @Test
    @DisplayName("reviewTask – approves with rating, creates a new review row")
    void reviewTask_success_newReview() {
        Task task = new Task();
        task.setId(taskId);
        task.setTitle("T");
        task.setStatus(TaskStatus.IN_REVIEW);
        User assignee = new User();
        assignee.setId(assigneeId);
        task.setAssignee(assignee);

        Task saved = new Task();
        saved.setId(taskId);
        saved.setTitle("T");
        saved.setStatus(TaskStatus.APPROVED);
        saved.setAssignee(assignee);

        User reviewer = new User();
        reviewer.setId(actorId);

        ReviewTaskRequest request = ReviewTaskRequest.builder()
                .status(TaskStatus.APPROVED)
                .rating(5)
                .comment("Great work")
                .build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        doNothing().when(taskWorkflowService).assertCanChangeStatus(task, TaskStatus.APPROVED, actor);
        when(taskRepository.save(task)).thenReturn(saved);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(reviewer));
        when(taskReviewRepository.findByTaskId(taskId)).thenReturn(Optional.empty());

        ArgumentCaptor<TaskReview> reviewCaptor = ArgumentCaptor.forClass(TaskReview.class);
        when(taskReviewRepository.save(reviewCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(taskReviewMapper.toResponse(any(TaskReview.class))).thenReturn(new TaskReviewResponse());

        taskService.reviewTask(taskId, request);

        verify(taskWorkflowService).assertCanChangeStatus(task, TaskStatus.APPROVED, actor);
        verify(eventPublisher).publishEvent(any(TaskStatusChangedEvent.class));

        TaskReview persisted = reviewCaptor.getValue();
        assertThat(persisted.getTask()).isEqualTo(saved);
        assertThat(persisted.getReviewer()).isEqualTo(reviewer);
        assertThat(persisted.getRating()).isEqualTo(5);
        assertThat(persisted.getComment()).isEqualTo("Great work");
        assertThat(persisted.getDecision()).isEqualTo(TaskStatus.APPROVED);
    }

    @Test
    @DisplayName("reviewTask – re-reviewing overwrites the existing row instead of inserting a new one")
    void reviewTask_success_overwritesExistingReview() {
        Task task = new Task();
        task.setId(taskId);
        task.setStatus(TaskStatus.IN_REVIEW);

        Task saved = new Task();
        saved.setId(taskId);
        saved.setStatus(TaskStatus.REJECTED);

        User reviewer = new User();
        reviewer.setId(actorId);

        TaskReview existing = new TaskReview();
        existing.setId(UUID.randomUUID());
        existing.setRating(3);
        existing.setComment("Needs more work");
        existing.setDecision(TaskStatus.APPROVED);

        ReviewTaskRequest request = ReviewTaskRequest.builder()
                .status(TaskStatus.REJECTED)
                .rating(2)
                .comment("Still not right")
                .build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        doNothing().when(taskWorkflowService).assertCanChangeStatus(task, TaskStatus.REJECTED, actor);
        when(taskRepository.save(task)).thenReturn(saved);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(reviewer));
        when(taskReviewRepository.findByTaskId(taskId)).thenReturn(Optional.of(existing));
        when(taskReviewRepository.save(any(TaskReview.class))).thenAnswer(inv -> inv.getArgument(0));
        when(taskReviewMapper.toResponse(any(TaskReview.class))).thenReturn(new TaskReviewResponse());

        taskService.reviewTask(taskId, request);

        verify(taskReviewRepository, never()).save(argThat(r -> r != existing));
        assertThat(existing.getRating()).isEqualTo(2);
        assertThat(existing.getComment()).isEqualTo("Still not right");
        assertThat(existing.getDecision()).isEqualTo(TaskStatus.REJECTED);
    }

    @Test
    @DisplayName("reviewTask – rejects a status that isn't APPROVED/REJECTED")
    void reviewTask_invalidDecision_throws() {
        ReviewTaskRequest request = ReviewTaskRequest.builder()
                .status(TaskStatus.DONE)
                .rating(4)
                .comment("N/A")
                .build();

        assertThatThrownBy(() -> taskService.reviewTask(taskId, request))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(taskRepository);
    }

    @Test
    @DisplayName("getReview – returns the persisted review")
    void getReview_success() {
        Task task = new Task();
        task.setId(taskId);

        TaskReview review = new TaskReview();
        review.setId(UUID.randomUUID());

        TaskReviewResponse response = new TaskReviewResponse();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        doNothing().when(taskAuthorizationService).assertCanView(actor, task);
        when(taskReviewRepository.findByTaskId(taskId)).thenReturn(Optional.of(review));
        when(taskReviewMapper.toResponse(review)).thenReturn(response);

        TaskReviewResponse result = taskService.getReview(taskId, actor);

        assertThat(result).isSameAs(response);
    }

    @Test
    @DisplayName("getReview – returns null when the task hasn't been reviewed yet")
    void getReview_notYetReviewed_returnsNull() {
        Task task = new Task();
        task.setId(taskId);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        doNothing().when(taskAuthorizationService).assertCanView(actor, task);
        when(taskReviewRepository.findByTaskId(taskId)).thenReturn(Optional.empty());

        assertThat(taskService.getReview(taskId, actor)).isNull();
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
}
