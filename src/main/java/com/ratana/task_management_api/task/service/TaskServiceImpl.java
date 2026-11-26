package com.ratana.task_management_api.task.service;

import com.ratana.task_management_api.task.workflow.TaskWorkflowService;

import com.ratana.task_management_api.task.dto.response.TaskCountsResponse;
import com.ratana.task_management_api.task.dto.request.AssignMultipleUsersRequest;
import com.ratana.task_management_api.task.dto.request.AssignTaskRequest;
import com.ratana.task_management_api.task.dto.request.CreateTaskRequest;
import com.ratana.task_management_api.task.dto.request.TaskFilterRequest;
import com.ratana.task_management_api.task.dto.request.UpdateTaskRequest;
import com.ratana.task_management_api.task.dto.response.ManagerAssignedKpiResponse;
import com.ratana.task_management_api.common.pagination.PageResponse;
import com.ratana.task_management_api.task.dto.response.TaskAssignmentResponse;
import com.ratana.task_management_api.task.dto.response.TaskResponse;
import com.ratana.task_management_api.task.enums.AssignmentMode;
import com.ratana.task_management_api.task.enums.SubmissionStatus;
import com.ratana.task_management_api.task.enums.TaskAuditAction;
import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.task.enums.ViewScope;
import com.ratana.task_management_api.task.event.TaskEvent;
import com.ratana.task_management_api.common.exception.BadRequestException;
import com.ratana.task_management_api.task.exception.InvalidAssignmentException;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.task.exception.TaskNotFoundException;
import com.ratana.task_management_api.task.exception.TaskStateConflictException;
import com.ratana.task_management_api.task.exception.UnauthorizedTaskAccessException;
import com.ratana.task_management_api.task.mapper.TaskMapper;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.entity.TaskAssignment;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.organization.repository.DepartmentRepository;
import com.ratana.task_management_api.task.repository.TaskCountRepository;
import com.ratana.task_management_api.task.repository.TaskRepository;
import com.ratana.task_management_api.task.repository.TaskSubmissionRepository;
import com.ratana.task_management_api.user.repository.UserRepository;
import com.ratana.task_management_api.infrastructure.security.SecurityUtils;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.task.specification.TaskSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final TaskAssignmentService taskAssignmentService;
    private final TaskAuditService taskAuditService;
    private final TaskGroupService taskGroupService;
    private final TaskKpiService taskKpiService;

    /** Overdue filters in task lists use the business timezone's "today". */
    @Value("${app.reminder.default-timezone:Asia/Phnom_Penh}")
    void setReportZone(String zone) {
        TaskSpecifications.setBusinessZone(ZoneId.of(zone));
    }

    @Override
    @Transactional
    public TaskResponse createTask(CreateTaskRequest createTaskRequest, UserPrincipal userPrincipal) {
        UserPrincipal actor = resolveActor(userPrincipal);
        taskAuthorizationService.assertCanCreate(actor);

        log.debug("User {} creating task \"{}\"", actor.getId(), createTaskRequest.getTitle());
        User creator = userRepository.findById(actor.getId())
                .orElseThrow(() -> {
                    log.warn("Task creation failed: creator {} not found", actor.getId());
                    return new ResourceNotFoundException("Creator not found");
                });

        List<UUID> memberIds = createTaskRequest.getAssigneeIds() == null ? List.of()
                : createTaskRequest.getAssigneeIds().stream().filter(Objects::nonNull).distinct().toList();
        int assignmentModes = (createTaskRequest.getAssigneeId() != null ? 1 : 0)
                + (memberIds.isEmpty() ? 0 : 1)
                + (createTaskRequest.wholeDepartment() ? 1 : 0)
                + (createTaskRequest.getAssignPositionId() != null ? 1 : 0);
        if (assignmentModes > 1) {
            throw new BadRequestException(
                    "Use only one of assigneeId, assigneeIds, assignWholeDepartment or assignPositionId.");
        }
        // A one-person list is just a normal task.
        if (memberIds.size() == 1) {
            createTaskRequest.setAssigneeId(memberIds.get(0));
            memberIds = List.of();
        }
        boolean multiple = !memberIds.isEmpty() || createTaskRequest.wholeDepartment()
                || createTaskRequest.getAssignPositionId() != null;
        if (multiple && createTaskRequest.getParentTaskId() != null) {
            throw new BadRequestException("A task for several people cannot be a subtask.");
        }
        boolean shared = createTaskRequest.getAssignmentMode() == AssignmentMode.SHARED;

        Task task = taskMapper.toEntity(createTaskRequest);
        task.setCreator(creator);
        task.setStatus(TaskStatus.TODO);
        task.setDepartment(loadDepartment(createTaskRequest.getDepartmentId()));

        if (multiple) {
            List<User> people = resolvePeople(task, createTaskRequest, memberIds, creator, actor);
            return shared
                    ? createSharedTask(task, createTaskRequest, people, creator)
                    : createGroupTask(task, createTaskRequest, people, creator);
        }

        Task parent = null;
        if (createTaskRequest.getParentTaskId() != null) {
            parent = loadParentForNewSubtask(createTaskRequest, actor);
            task.setParentTask(parent);
            if (parent.isGroupTask()) {
                task.setDepartment(parent.getDepartment());   // members share the group's department
            }
        }

        if (createTaskRequest.getAssigneeId() != null) {
            User assignee = loadUser(createTaskRequest.getAssigneeId(), "Assignee not found");
            assertAssignable(assignee);
            taskAuthorizationService.assertCanAssign(actor, task, assignee);
            task.setAssignee(assignee);
            task.setAssignedBy(creator);
        }

        Task saved = taskRepository.save(task);
        log.info("Task {} created by {}", saved.getId(), creator.getId());
        taskAuditService.record(saved, creator.getId(), TaskAuditAction.TASK_CREATED, TaskAuditService.values(
                "title", saved.getTitle(),
                "priority", saved.getPriority(),
                "departmentId", saved.getDepartment() != null ? saved.getDepartment().getId() : null,
                "assigneeId", idOf(saved.getAssignee()),
                "parentTaskId", parent != null ? parent.getId() : null,
                "dueDate", saved.getDueDate()));

        if (parent != null) {
            parent.getSubtasks().add(saved);
            if (parent.isGroupTask()) {
                taskAuditService.record(parent, creator.getId(), TaskAuditAction.GROUP_MEMBER_ADDED,
                        TaskAuditService.values("userId", idOf(saved.getAssignee()), "subtaskId", saved.getId()));
                taskGroupService.sync(parent, creator.getId());   // a new TODO member reopens a closed group
            }
        }
        if (saved.getAssignee() != null) {
            publishAssigned(saved, creator.getId(), creator.getFullName());
        }
        return taskMapper.toResponse(saved);
    }

    /**
     * The people a multi-person task goes to: the listed users, every active member of the task's
     * department, or every active holder of a position (the last two except the creator). Each must be
     * active and assignable by the actor.
     */
    private List<User> resolvePeople(Task task, CreateTaskRequest request, List<UUID> memberIds,
                                     User creator, UserPrincipal actor) {
        List<User> people;
        if (request.wholeDepartment()) {
            people = userRepository.findByDepartmentId(task.getDepartment().getId()).stream()
                    .filter(User::isAccountEnabled)
                    .filter(user -> !user.getId().equals(creator.getId()))
                    .toList();
            if (people.isEmpty()) {
                throw new BadRequestException("This department has no active members to assign.");
            }
        } else if (request.getAssignPositionId() != null) {
            people = userRepository.findByPositionId(request.getAssignPositionId()).stream()
                    .filter(User::isAccountEnabled)
                    .filter(user -> !user.getId().equals(creator.getId()))
                    .toList();
            if (people.isEmpty()) {
                throw new BadRequestException("Nobody active holds this position, so there is no one to assign.");
            }
        } else {
            people = new ArrayList<>();
            for (UUID userId : memberIds) {
                User user = loadUser(userId, "Assignee not found");
                assertAssignable(user);
                people.add(user);
            }
        }
        for (User person : people) {
            taskAuthorizationService.assertCanAssign(actor, task, person);
        }
        return people;
    }

    /**
     * One task for everyone: the first person is the primary assignee, the rest are collaborators.
     * Any of them can submit; one approval completes it for all (and all get completion credit).
     */
    private TaskResponse createSharedTask(Task task, CreateTaskRequest request, List<User> people, User creator) {
        task.setAssignee(people.get(0));
        task.setAssignedBy(creator);
        Task saved = taskRepository.save(task);
        List<TaskAssignment> collaborators = taskAssignmentService.assignUsers(
                saved, people.subList(1, people.size()), creator);
        saved = taskRepository.saveAndFlush(saved);

        List<UUID> collaboratorIds = collaborators.stream().map(a -> a.getUser().getId()).toList();
        log.info("Shared task {} created by {}: assignee {} + {} collaborator(s), chosen by {}", saved.getId(),
                creator.getId(), saved.getAssignee().getId(), collaboratorIds.size(), request.audienceKind());
        taskAuditService.record(saved, creator.getId(), TaskAuditAction.TASK_CREATED, TaskAuditService.values(
                "title", saved.getTitle(),
                "priority", saved.getPriority(),
                "departmentId", saved.getDepartment().getId(),
                "assignmentMode", AssignmentMode.SHARED.name(),
                "wholeDepartment", request.wholeDepartment(),
                "positionId", request.getAssignPositionId(),
                "assigneeId", saved.getAssignee().getId(),
                "collaboratorIds", collaboratorIds,
                "dueDate", saved.getDueDate()));

        publishAssigned(saved, creator.getId(), creator.getFullName());
        if (!collaboratorIds.isEmpty()) {
            eventPublisher.publishEvent(TaskEvent.collaboratorsAdded(
                    saved, creator.getId(), creator.getFullName(), collaboratorIds));
        }
        return taskMapper.toResponse(saved);
    }

    /**
     * One parent (no assignee, status derived from members) plus one TODO member subtask per person,
     * all in this transaction. Each member gets the normal assignment notification.
     */
    private TaskResponse createGroupTask(Task group, CreateTaskRequest request, List<User> members, User creator) {
        group.setGroupTask(true);
        group.setAssignedBy(creator);   // so it shows in the creator's "tasks I assigned" list
        Task savedGroup = taskRepository.save(group);

        List<Task> created = new ArrayList<>();
        for (User member : members) {
            Task subtask = taskMapper.toEntity(request);
            subtask.setCreator(creator);
            subtask.setStatus(TaskStatus.TODO);
            subtask.setDepartment(savedGroup.getDepartment());
            subtask.setParentTask(savedGroup);
            subtask.setAssignee(member);
            subtask.setAssignedBy(creator);
            created.add(taskRepository.save(subtask));
        }
        savedGroup.getSubtasks().addAll(created);

        log.info("Group task {} created by {} with {} member(s), chosen by {}", savedGroup.getId(), creator.getId(),
                created.size(), request.audienceKind());
        taskAuditService.record(savedGroup, creator.getId(), TaskAuditAction.TASK_CREATED, TaskAuditService.values(
                "title", savedGroup.getTitle(),
                "priority", savedGroup.getPriority(),
                "departmentId", savedGroup.getDepartment().getId(),
                "groupTask", true,
                "wholeDepartment", request.wholeDepartment(),
                "positionId", request.getAssignPositionId(),
                "memberIds", members.stream().map(User::getId).toList(),
                "dueDate", savedGroup.getDueDate()));
        for (Task subtask : created) {
            taskAuditService.record(subtask, creator.getId(), TaskAuditAction.TASK_CREATED, TaskAuditService.values(
                    "title", subtask.getTitle(),
                    "assigneeId", subtask.getAssignee().getId(),
                    "parentTaskId", savedGroup.getId(),
                    "dueDate", subtask.getDueDate()));
            publishAssigned(subtask, creator.getId(), creator.getFullName());
        }
        return taskMapper.toResponse(savedGroup);
    }

    /**
     * Parent of a subtask being created: active, not itself a subtask, and plannable by the actor.
     * A group parent also needs an assignee (the member being added) who isn't already a member.
     */
    private Task loadParentForNewSubtask(CreateTaskRequest request, UserPrincipal actor) {
        Task parent = loadTask(request.getParentTaskId());
        if (!parent.isActive()) {
            throw new BadRequestException("The parent task is archived.");
        }
        if (parent.getParentTask() != null) {
            throw new BadRequestException("Subtasks cannot have their own subtasks.");
        }
        if (!taskAuthorizationService.canEditPlanningFields(actor, parent)) {
            throw new UnauthorizedTaskAccessException("You cannot add subtasks to this task.");
        }
        if (parent.isGroupTask()) {
            if (request.getAssigneeId() == null) {
                throw new BadRequestException("Adding someone to a group task requires assigneeId.");
            }
            boolean alreadyMember = parent.getSubtasks().stream()
                    .filter(Task::isActive)
                    .anyMatch(subtask -> subtask.getAssignee() != null
                            && subtask.getAssignee().getId().equals(request.getAssigneeId()));
            if (alreadyMember) {
                throw new BadRequestException("This person is already a member of the group task.");
            }
        } else if (isTerminal(parent)) {
            throw new TaskStateConflictException(TaskStateConflictException.TASK_TERMINAL,
                    "A %s task cannot get new subtasks. Reopen it first.".formatted(parent.getStatus()));
        }
        return parent;
    }

    @Override
    @Transactional
    public TaskResponse assignTask(UUID taskId, AssignTaskRequest assignTaskRequest) {
        UserPrincipal actor = SecurityUtils.getCurrentUser();
        Task task = loadTask(taskId);
        User assignee = loadUser(assignTaskRequest.getAssigneeId(), "Assignee not found");

        assertNotGroupTask(task, "Add a person by creating a subtask with parentTaskId set to this task.");
        assertNotTerminal(task, "reassigned");
        // The new assignee would inherit someone else's pending submission (and its rework if rejected).
        if (taskSubmissionRepository.existsByTaskIdAndStatus(taskId, SubmissionStatus.PENDING)) {
            throw new TaskStateConflictException(TaskStateConflictException.USE_REVIEW_ENDPOINT,
                    "This task has a submission waiting for review. Approve or reject it before reassigning.");
        }
        assertAssignable(assignee);
        taskAuthorizationService.assertCanAssign(actor, task, assignee);

        User assigner = loadUser(actor.getId(), "Assigner not found");
        UUID previousAssigneeId = idOf(task.getAssignee());
        task.setAssignee(assignee);
        task.setAssignedBy(assigner);
        Task saved = taskRepository.save(task);

        log.info("Task {} assigned to user {} by {}", taskId, assignee.getId(), actor.getId());
        taskAuditService.record(saved, actor.getId(), TaskAuditAction.ASSIGNEE_CHANGED,
                TaskAuditService.values("assigneeId", previousAssigneeId),
                TaskAuditService.values("assigneeId", assignee.getId()), null);
        publishAssigned(saved, actor.getId(), assigner.getFullName());
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
    public TaskResponse updateOwnTask(UUID taskId, UpdateTaskRequest updateTaskRequest) {
        UserPrincipal actor = SecurityUtils.getCurrentUser();
        Task task = loadTask(taskId);
        taskAuthorizationService.assertCanUpdate(actor, task);
        if (isTerminal(task)) {
            log.warn("Update of task {} rejected: task is {}", taskId, task.getStatus());
            throw new TaskStateConflictException(TaskStateConflictException.TASK_NOT_EDITABLE,
                    "A %s task cannot be edited. Reopen it first.".formatted(task.getStatus()));
        }

        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        diff(before, after, "title", task.getTitle(), updateTaskRequest.getTitle());
        diff(before, after, "description", task.getDescription(), updateTaskRequest.getDescription());
        diff(before, after, "priority", task.getPriority(), updateTaskRequest.getPriority());
        diff(before, after, "dueDate", task.getDueDate(), updateTaskRequest.getDueDate());

        // Q-09: someone who can only update their own task may change the description, nothing else.
        boolean planningChanged = after.containsKey("title") || after.containsKey("priority") || after.containsKey("dueDate");
        if (planningChanged && !taskAuthorizationService.canEditPlanningFields(actor, task)) {
            log.warn("Update of task {} by {} rejected: assignee tried to change {}", taskId, actor.getId(), after.keySet());
            throw new UnauthorizedTaskAccessException(
                    "You can only change the description of this task. Ask its manager to change the title, priority or due date.");
        }

        task.setTitle(updateTaskRequest.getTitle());
        task.setDescription(updateTaskRequest.getDescription());
        task.setPriority(updateTaskRequest.getPriority());
        task.setDueDate(updateTaskRequest.getDueDate());
        Task saved = taskRepository.save(task);
        log.info("Task {} updated by {}", taskId, actor.getId());
        // A group task's people are on its member subtasks, so they're the ones told about the change.
        List<Task> told = saved.isGroupTask() ? propagateToMembers(saved) : List.of(saved);
        if (!after.isEmpty()) {
            taskAuditService.record(saved, actor.getId(), TaskAuditAction.TASK_UPDATED, before, after, null);
        }
        String summary = TaskChangeSummary.of(before, after);
        if (summary != null) {
            String actorName = nameOf(actor);
            told.forEach(member -> eventPublisher.publishEvent(
                    TaskEvent.detailsChanged(member, actor.getId(), actorName, summary)));
        }
        return taskMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public TaskResponse changeStatus(UUID id, String status) {
        return changeStatus(id, status, null);
    }

    @Override
    @Transactional
    public TaskResponse changeStatus(UUID id, String status, String reason) {
        UserPrincipal actor = SecurityUtils.getCurrentUser();
        Task task = loadTask(id);
        TaskStatus targetStatus = parseStatus(status);
        assertNotGroupTask(task, "Its status follows its members' subtasks; change a member's subtask instead.");

        // Review states are owned by the submission workflow, so every approval has a submission
        // behind it and the "no reviewing your own work" rule always applies (DEF-10).
        if (targetStatus == TaskStatus.IN_REVIEW) {
            throw new TaskStateConflictException(TaskStateConflictException.USE_SUBMISSION_ENDPOINT,
                    "Submit work (POST /api/v1/tasks/{id}/submissions) to send a task for review.");
        }
        if (targetStatus == TaskStatus.APPROVED || targetStatus == TaskStatus.REJECTED) {
            throw new TaskStateConflictException(TaskStateConflictException.USE_REVIEW_ENDPOINT,
                    "Approve or reject the task's submission (POST .../submissions/{submissionId}/review) instead.");
        }

        boolean reopening = isTerminal(task) && targetStatus == TaskStatus.TODO;
        String trimmedReason = trimToNull(reason);
        if (reopening && trimmedReason == null) {
            throw new BadRequestException("A reason is required to reopen a task.");
        }
        if (targetStatus == TaskStatus.BLOCKED && trimmedReason == null) {
            throw new BadRequestException("A reason is required to mark a task as blocked.");
        }

        taskWorkflowService.assertCanChangeStatus(task, targetStatus, actor);

        String oldStatus = task.getStatus().name();
        task.setStatus(targetStatus);
        Task saved = taskRepository.save(task);
        log.info("Task {} status changed {} -> {} by {}", id, oldStatus, targetStatus, actor.getId());
        taskAuditService.record(saved, actor.getId(),
                reopening ? TaskAuditAction.TASK_REOPENED : TaskAuditAction.STATUS_CHANGED,
                TaskAuditService.values("status", oldStatus),
                TaskAuditService.values("status", targetStatus.name()), trimmedReason);
        taskGroupService.onMemberChanged(saved, actor.getId());

        eventPublisher.publishEvent(TaskEvent.statusChanged(
                saved, actor.getId(), nameOf(actor), oldStatus, targetStatus.name(), trimmedReason));

        return taskMapper.toResponse(saved);
    }

    /**
     * Admin escape hatch outside the status graph (e.g. fixing a task stuck after a data error).
     * Requires a reason and is always audited as STATUS_OVERRIDDEN. Refused while a submission is
     * pending, so a submission can never be left PENDING on a task that is no longer in review.
     */
    @Override
    @Transactional
    public TaskResponse overrideStatus(UUID id, String status, String reason) {
        UserPrincipal actor = SecurityUtils.getCurrentUser();
        Task task = loadTask(id);
        TaskStatus targetStatus = parseStatus(status);
        String trimmedReason = trimToNull(reason);
        if (trimmedReason == null) {
            throw new BadRequestException("A reason is required to override a task's status.");
        }
        if (taskSubmissionRepository.existsByTaskIdAndStatus(id, SubmissionStatus.PENDING)) {
            throw new TaskStateConflictException(TaskStateConflictException.USE_REVIEW_ENDPOINT,
                    "This task has a submission waiting for review. Approve or reject it first.");
        }

        String oldStatus = task.getStatus().name();
        task.setStatus(targetStatus);
        Task saved = taskRepository.save(task);
        log.warn("Task {} status OVERRIDDEN {} -> {} by {}: {}", id, oldStatus, targetStatus, actor.getId(), trimmedReason);
        taskAuditService.record(saved, actor.getId(), TaskAuditAction.STATUS_OVERRIDDEN,
                TaskAuditService.values("status", oldStatus),
                TaskAuditService.values("status", targetStatus.name()), trimmedReason);
        taskGroupService.onMemberChanged(saved, actor.getId());
        eventPublisher.publishEvent(TaskEvent.statusChanged(
                saved, actor.getId(), nameOf(actor), oldStatus, targetStatus.name(), trimmedReason));
        return taskMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID taskId, UserPrincipal userPrincipal) {
        UserPrincipal actor = resolveActor(userPrincipal);
        Task task = loadTask(taskId);
        taskAuthorizationService.assertCanView(actor, task);
        log.debug("Task {} fetched by {}", taskId, actor.getId());
        return taskMapper.toResponse(task);
    }

    @Override
    @Transactional
    public void archiveTask(UUID taskId, UserPrincipal userPrincipal) {
        UserPrincipal actor = resolveActor(userPrincipal);
        Task task = loadTask(taskId);
        taskAuthorizationService.assertCanArchive(actor, task);

        task.setActive(false);
        int archivedSubtasks = 0;
        for (Task subtask : task.getSubtasks()) {
            if (subtask.isActive()) {
                subtask.setActive(false);
                archivedSubtasks++;
            }
        }
        taskRepository.save(task);
        taskAuditService.record(task, actor.getId(), TaskAuditAction.TASK_ARCHIVED,
                TaskAuditService.values("archivedSubtasks", archivedSubtasks));
        log.info("Task {} archived by {} ({} subtask(s))", taskId, actor.getId(), archivedSubtasks);
        taskGroupService.onMemberChanged(task, actor.getId());   // an archived member no longer counts
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> searchTasks(TaskFilterRequest filter, Pageable pageable, UserPrincipal userPrincipal) {
        UserPrincipal actor = resolveActor(userPrincipal);
        ViewScope scope = taskAuthorizationService.resolveViewScope(actor);

        Specification<Task> spec = visibleActiveSpec(actor, scope).and(TaskSpecifications.fromFilter(filter));

        // Newest first unless the client asks otherwise: without an order the database returns rows in
        // no particular order, so a just-created task could land outside the first page.
        Pageable ordered = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Task> page = taskRepository.findAll(spec, ordered);
        log.debug("Task search by {} (scope={}): {} result(s), page {}", actor.getId(), scope,
                page.getTotalElements(), pageable.getPageNumber());
        return PageResponse.from(page.map(taskMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public TaskCountsResponse countTasks(TaskFilterRequest filter, UserPrincipal userPrincipal) {
        UserPrincipal actor = resolveActor(userPrincipal);
        ViewScope scope = taskAuthorizationService.resolveViewScope(actor);

        // Status and overdue are what we break down by, so they're ignored as filters here.
        filter.setTaskStatus(null);
        filter.setOverdue(null);
        Specification<Task> spec = visibleActiveSpec(actor, scope).and(TaskSpecifications.fromFilter(filter));

        // One GROUP BY status query instead of a COUNT per status plus one for overdue.
        Map<TaskStatus, TaskCountRepository.StatusCount> counts = taskRepository.countByStatusWithOverdue(spec);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long total = 0;
        long overdue = 0;
        for (TaskStatus status : TaskStatus.values()) {   // every status, zero included, in enum order
            TaskCountRepository.StatusCount count = counts.get(status);
            byStatus.put(status.name(), count == null ? 0L : count.count());
            if (count != null) {
                total += count.count();
                overdue += count.overdue();
            }
        }
        return new TaskCountsResponse(total, overdue, byStatus);
    }

    /** Tasks the actor may see, minus archived ones. Shared by search and counts so they always agree. */
    private Specification<Task> visibleActiveSpec(UserPrincipal actor, ViewScope scope) {
        Collection<UUID> teamMemberIds = scope == ViewScope.TEAM
                ? userRepository.findDirectReportIds(actor.getId())
                : List.of();
        return TaskSpecifications.visibleTo(actor, scope, teamMemberIds)
                .and(TaskSpecifications.activeOnly()); // archived tasks never show in lists
    }

    @Override
    @Transactional
    public List<TaskAssignmentResponse> assignAdditionalUsers(UUID taskId, AssignMultipleUsersRequest request) {
        UserPrincipal actor = SecurityUtils.getCurrentUser();
        Task task = loadTask(taskId);
        assertNotGroupTask(task, "Add a person by creating a subtask with parentTaskId set to this task.");

        List<User> users = new ArrayList<>();
        for (UUID userId : new LinkedHashSet<>(request.getUserIds())) {
            User user = loadUser(userId, "Assignee not found");
            if (!user.isAccountEnabled()) {
                log.warn("Assignment to task {} rejected: user {} is inactive", taskId, userId);
                throw new BadRequestException("User is not active: " + userId);
            }
            taskAuthorizationService.assertCanAssign(actor, task, user);
            users.add(user);
        }

        assertNotTerminal(task, "given new collaborators");
        User assignedBy = loadUser(actor.getId(), "Assigner not found");
        List<TaskAssignment> added = taskAssignmentService.assignUsers(task, users, assignedBy);
        taskRepository.saveAndFlush(task);
        if (!added.isEmpty()) {
            taskAuditService.record(task, actor.getId(), TaskAuditAction.COLLABORATORS_ADDED, TaskAuditService.values(
                    "userIds", added.stream().map(assignment -> assignment.getUser().getId()).toList()));
        }

        log.info("Task {}: {} collaborator(s) added by {}", taskId, added.size(), actor.getId());
        if (!added.isEmpty()) {
            eventPublisher.publishEvent(TaskEvent.collaboratorsAdded(task, actor.getId(), assignedBy.getFullName(),
                    added.stream().map(assignment -> assignment.getUser().getId()).toList()));
        }
        return added.stream().map(this::toAssignmentResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ManagerAssignedKpiResponse getAssignedKpi(UserPrincipal userPrincipal) {
        return taskKpiService.assignedKpi(resolveActor(userPrincipal));
    }

    private TaskAssignmentResponse toAssignmentResponse(TaskAssignment assignment) {
        return TaskAssignmentResponse.builder()
                .id(assignment.getId())
                .taskId(assignment.getTask().getId())
                .user(taskMapper.mapUser(assignment.getUser()))
                .assignedBy(taskMapper.mapUser(assignment.getAssignedBy()))
                .status(assignment.getStatus())
                .assignedAt(assignment.getAssignedAt())
                .build();
    }

    private void publishAssigned(Task saved, UUID assignedById, String assignedByName) {
        eventPublisher.publishEvent(TaskEvent.assigned(saved, assignedById, assignedByName));
    }

    /** Editing a group task edits the brief every unfinished member is working from; returns those members. */
    private List<Task> propagateToMembers(Task group) {
        List<Task> updated = new ArrayList<>();
        for (Task member : group.getSubtasks()) {
            if (!member.isActive() || isTerminal(member)) {
                continue;
            }
            member.setTitle(group.getTitle());
            member.setDescription(group.getDescription());
            member.setPriority(group.getPriority());
            member.setDueDate(group.getDueDate());
            updated.add(member);
        }
        log.debug("Group task {}: edit copied to {} unfinished member(s)", group.getId(), updated.size());
        return updated;
    }

    /** Full name for notification text; the principal's username is an email address. */
    private String nameOf(UserPrincipal actor) {
        return userRepository.findById(actor.getId()).map(User::getFullName).orElse(actor.getUsername());
    }

    private static void assertNotGroupTask(Task task, String hint) {
        if (task.isGroupTask()) {
            log.warn("Change to group task {} rejected: group tasks are managed through their members", task.getId());
            throw new TaskStateConflictException(TaskStateConflictException.GROUP_TASK_MANAGED,
                    "This is a group task. " + hint);
        }
    }

    private static void assertAssignable(User user) {
        if (!user.isAccountEnabled()) {
            log.warn("Assignment rejected: user {} is inactive", user.getId());
            throw new InvalidAssignmentException("Cannot assign an inactive user.");
        }
    }

    /** tasks.status is nullable in the schema; treat a missing status as not terminal. */
    private static boolean isTerminal(Task task) {
        return task.getStatus() != null && task.getStatus().isTerminal();
    }

    private static void assertNotTerminal(Task task, String action) {
        if (isTerminal(task)) {
            log.warn("Task {} cannot be {}: it is {}", task.getId(), action, task.getStatus());
            throw new TaskStateConflictException(TaskStateConflictException.TASK_TERMINAL,
                    "A %s task cannot be %s.".formatted(task.getStatus(), action));
        }
    }

    /** Records a field in both maps only when its value actually changes. */
    private static void diff(Map<String, Object> before, Map<String, Object> after, String field, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            before.put(field, oldValue);
            after.put(field, newValue);
        }
    }

    private static UUID idOf(User user) {
        return user != null ? user.getId() : null;
    }

    private static String trimToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private UserPrincipal resolveActor(UserPrincipal provided) {
        return provided != null ? provided : SecurityUtils.getCurrentUser();
    }

    private TaskStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            log.warn("Status change rejected: status is blank");
            throw new IllegalArgumentException("Task status is required");
        }
        try {
            return TaskStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            log.warn("Status change rejected: invalid status {}", status);
            throw new IllegalArgumentException("Invalid task status: " + status);
        }
    }

    private User loadUser(UUID userId, String message) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("{}: {}", message, userId);
                    return new ResourceNotFoundException(message);
                });
    }

    /** Archived (is_active = false) tasks are treated as not found everywhere in this service. */
    private Task loadTask(UUID id) {
        return taskRepository.findById(id)
                .filter(Task::isActive)
                .orElseThrow(() -> {
                    log.warn("Task not found: {}", id);
                    return new TaskNotFoundException("Task not found: " + id);
                });
    }

    private Department loadDepartment(UUID id) {
        if (id == null) {
            log.warn("Task creation rejected: department id is missing");
            throw new IllegalArgumentException("Department id is required");
        }
        return departmentRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Department not found: {}", id);
                    return new ResourceNotFoundException("Department not found: " + id);
                });
    }
}
