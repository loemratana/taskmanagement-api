package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.dto.request.AssignMultipleUsersRequest;
import com.ratana.task_management_api.dto.request.AssignTaskRequest;
import com.ratana.task_management_api.dto.request.CreateTaskRequest;
import com.ratana.task_management_api.dto.request.TaskFilterRequest;
import com.ratana.task_management_api.dto.request.UpdateTaskRequest;
import com.ratana.task_management_api.dto.response.PageResponse;
import com.ratana.task_management_api.dto.response.TaskAssignmentResponse;
import com.ratana.task_management_api.dto.response.TaskResponse;
import com.ratana.task_management_api.enums.TaskStatus;
import com.ratana.task_management_api.enums.ViewScope;
import com.ratana.task_management_api.event.TaskAssignedEvent;
import com.ratana.task_management_api.event.TaskStatusChangedEvent;
import com.ratana.task_management_api.exception.ResourceNotFoundException;
import com.ratana.task_management_api.exception.TaskNotFoundException;
import com.ratana.task_management_api.mapper.TaskMapper;
import com.ratana.task_management_api.model.Department;
import com.ratana.task_management_api.model.Task;
import com.ratana.task_management_api.model.TaskAssignment;
import com.ratana.task_management_api.model.User;
import com.ratana.task_management_api.repository.DepartmentRepository;
import com.ratana.task_management_api.repository.TaskRepository;
import com.ratana.task_management_api.repository.UserRepository;
import com.ratana.task_management_api.security.SecurityUtils;
import com.ratana.task_management_api.security.UserPrincipal;
import com.ratana.task_management_api.service.TaskService;
import com.ratana.task_management_api.utility.TaskSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Domain task operations. Authorization is delegated to {@link TaskAuthorizationService}
 * / {@link TaskWorkflowService}; notifications are triggered only via domain events.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TaskWorkflowService taskWorkflowService;
    private final TaskMapper taskMapper;
    private final TaskAuthorizationService taskAuthorizationService;
    private final TaskAssignmentService taskAssignmentService;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public TaskResponse createTask(CreateTaskRequest createTaskRequest, UserPrincipal userPrincipal) {
        UserPrincipal actor = resolveActor(userPrincipal);
        taskAuthorizationService.assertCanCreate(actor);

        User creator = userRepository.findById(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found"));

        Task task = taskMapper.toEntity(createTaskRequest);
        task.setCreator(creator);
        task.setStatus(TaskStatus.TODO);
        task.setTaskCode(generateTaskCode());
        task.setDepartment(loadDepartment(createTaskRequest.getDepartmentId()));

        if (createTaskRequest.getParentTaskId() != null) {
            task.setParentTask(loadTask(createTaskRequest.getParentTaskId()));
        }

        if (createTaskRequest.getAssigneeId() != null) {
            User assignee = loadUser(createTaskRequest.getAssigneeId(), "Assignee not found");
            taskAuthorizationService.assertCanAssign(actor, task, assignee);
            task.setAssignee(assignee);
            task.setAssignedBy(creator);
        }

        Task saved = taskRepository.save(task);
        log.info("Task {} created by {}", saved.getId(), creator.getId());

        if (saved.getAssignee() != null) {
            publishAssigned(saved, creator.getId(), creator.getFullName());
        }
        return taskMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public TaskResponse assignTask(UUID taskId, AssignTaskRequest assignTaskRequest) {
        UserPrincipal actor = SecurityUtils.getCurrentUser();
        Task task = loadTask(taskId);
        User assignee = loadUser(assignTaskRequest.getAssigneeId(), "Assignee not found");

        taskAuthorizationService.assertCanAssign(actor, task, assignee);

        User assigner = loadUser(actor.getId(), "Assigner not found");
        task.setAssignee(assignee);
        task.setAssignedBy(assigner);
        Task saved = taskRepository.save(task);

        log.info("Task {} assigned to user {} by {}", taskId, assignee.getId(), actor.getId());
        publishAssigned(saved, actor.getId(), actor.getUsername());
        return taskMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public TaskResponse assignTask(UUID taskId, UUID userId) {
        AssignTaskRequest request = new AssignTaskRequest();
        request.setAssigneeId(userId);
        return assignTask(taskId, request);
    }

    @Override
    @Transactional
    public List<TaskAssignmentResponse> assignUsers(UUID taskId, AssignMultipleUsersRequest request) {
        UserPrincipal actor = SecurityUtils.getCurrentUser();
        Task task = loadTask(taskId);

        List<User> targets = request.getUserIds().stream()
                .map(id -> loadUser(id, "Assignee not found: " + id))
                .toList();
        targets.forEach(target -> taskAuthorizationService.assertCanAssign(actor, task, target));

        List<TaskAssignment> created = taskAssignmentService.assignUsers(task, request.getUserIds(), actor);
        Task saved = taskRepository.save(task);
        log.info("Task {} assigned to {} additional user(s) by {}", taskId, created.size(), actor.getId());

        created.forEach(assignment -> eventPublisher.publishEvent(new TaskAssignedEvent(
                this,
                saved.getId(),
                saved.getTitle(),
                assignment.getUser().getId(),
                actor.getId(),
                actor.getUsername(),
                saved.getDepartment().getId()
        )));

        return created.stream().map(taskAssignmentService::toResponse).toList();
    }

    @Override
    @Transactional
    public TaskResponse updateOwnTask(UUID taskId, UpdateTaskRequest updateTaskRequest) {
        UserPrincipal actor = SecurityUtils.getCurrentUser();
        Task task = loadTask(taskId);
        taskAuthorizationService.assertCanUpdate(actor, task);

        task.setTitle(updateTaskRequest.getTitle());
        task.setDescription(updateTaskRequest.getDescription());
        task.setPriority(updateTaskRequest.getPriority());
        task.setDueDate(updateTaskRequest.getDueDate());
        Task saved = taskRepository.save(task);
        log.info("Task {} updated by {}", taskId, actor.getId());
        return taskMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public TaskResponse changeStatus(UUID id, String status) {
        UserPrincipal actor = SecurityUtils.getCurrentUser();
        Task task = loadTask(id);
        TaskStatus targetStatus = parseStatus(status);

        taskWorkflowService.assertCanChangeStatus(task, targetStatus, actor);

        TaskStatus previousStatus = task.getStatus();
        String oldStatus = previousStatus.name();
        task.setStatus(targetStatus);
        if (targetStatus == TaskStatus.DONE) {
            task.setCompletedAt(Instant.now());
        } else if (previousStatus == TaskStatus.DONE) {
            task.setCompletedAt(null);
        }
        Task saved = taskRepository.save(task);
        log.info("Task {} status changed {} -> {} by {}", id, oldStatus, targetStatus, actor.getId());

        if (saved.getAssignee() != null
                && !saved.getAssignee().getId().equals(actor.getId())) {
            eventPublisher.publishEvent(new TaskStatusChangedEvent(
                    this,
                    saved.getId(),
                    saved.getTitle(),
                    saved.getAssignee().getId(),
                    oldStatus,
                    targetStatus.name(),
                    actor.getId(),
                    actor.getUsername()
            ));
        }

        return taskMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID taskId, UserPrincipal userPrincipal) {
        UserPrincipal actor = resolveActor(userPrincipal);
        Task task = loadTask(taskId);
        taskAuthorizationService.assertCanView(actor, task);
        return taskMapper.toResponse(task);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> searchTasks(TaskFilterRequest filter, Pageable pageable, UserPrincipal userPrincipal) {
        UserPrincipal actor = resolveActor(userPrincipal);
        ViewScope scope = taskAuthorizationService.resolveViewScope(actor);

        Collection<UUID> teamMemberIds = scope == ViewScope.TEAM
                ? userRepository.findDirectReportIds(actor.getId())
                : List.of();

        Specification<Task> spec = TaskSpecifications.visibleTo(actor, scope, teamMemberIds)
                .and(TaskSpecifications.fromFilter(filter));

        Page<Task> page = taskRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(taskMapper::toResponse));
    }

    private void publishAssigned(Task saved, UUID assignedById, String assignedByName) {
        eventPublisher.publishEvent(new TaskAssignedEvent(
                this,
                saved.getId(),
                saved.getTitle(),
                saved.getAssignee().getId(),
                assignedById,
                assignedByName,
                saved.getDepartment().getId()
        ));
    }

    private UserPrincipal resolveActor(UserPrincipal provided) {
        return provided != null ? provided : SecurityUtils.getCurrentUser();
    }

    private TaskStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Task status is required");
        }
        try {
            return TaskStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid task status: " + status);
        }
    }

    private User loadUser(UUID userId, String message) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(message));
    }

    private Task loadTask(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found: " + id));
    }

    private String generateTaskCode() {
        return "TASK-%06d".formatted(taskRepository.nextTaskCodeSequence());
    }

    private Department loadDepartment(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Department id is required");
        }
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + id));
    }
}
