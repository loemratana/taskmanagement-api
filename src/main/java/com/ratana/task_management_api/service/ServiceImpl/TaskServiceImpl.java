package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.dto.request.*;
import com.ratana.task_management_api.dto.response.KpiSummaryResponse;
import com.ratana.task_management_api.dto.response.ManagerAssignedKpiResponse;
import com.ratana.task_management_api.dto.response.PageResponse;
import com.ratana.task_management_api.dto.response.TaskAssignmentResponse;
import com.ratana.task_management_api.dto.response.TaskResponse;
import com.ratana.task_management_api.dto.response.TaskReviewResponse;
import com.ratana.task_management_api.dto.response.TeamAnalyticsResponse;
import com.ratana.task_management_api.enums.TaskStatus;
import com.ratana.task_management_api.enums.ViewScope;
import com.ratana.task_management_api.event.TaskAssignedEvent;
import com.ratana.task_management_api.event.TaskStatusChangedEvent;
import com.ratana.task_management_api.exception.BadRequestException;
import com.ratana.task_management_api.exception.ResourceNotFoundException;
import com.ratana.task_management_api.exception.TaskNotFoundException;
import com.ratana.task_management_api.mapper.TaskMapper;
import com.ratana.task_management_api.mapper.TaskReviewMapper;
import com.ratana.task_management_api.model.Department;
import com.ratana.task_management_api.model.Task;
import com.ratana.task_management_api.model.TaskAssignment;
import com.ratana.task_management_api.model.TaskReview;
import com.ratana.task_management_api.model.User;
import com.ratana.task_management_api.repository.AttachmentRepository;
import com.ratana.task_management_api.repository.DepartmentRepository;
import com.ratana.task_management_api.repository.TaskCommentRepository;
import com.ratana.task_management_api.repository.TaskRepository;
import com.ratana.task_management_api.repository.TaskReviewRepository;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Domain task operations. Authorization is delegated to {@link TaskAuthorizationService}
 * / {@link TaskWorkflowService}; notifications are triggered only via domain events.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private static final List<TaskStatus> FINISHED_STATUSES =
            List.of(TaskStatus.DONE, TaskStatus.APPROVED, TaskStatus.CANCELLED);

    private final TaskRepository taskRepository;
    private final TaskWorkflowService taskWorkflowService;
    private final TaskMapper taskMapper;
    private final TaskAuthorizationService taskAuthorizationService;
    private final TaskAssignmentService taskAssignmentService;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskReviewRepository taskReviewRepository;
    private final TaskReviewMapper taskReviewMapper;
    private final TaskCommentRepository taskCommentRepository;
    private final AttachmentRepository attachmentRepository;

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
    public List<TaskResponse> createTasksForGroup(BulkCreateTaskRequest request, UserPrincipal userPrincipal) {

        UserPrincipal actor = resolveActor(userPrincipal);

        taskAuthorizationService.assertCanCreate(actor);

        User creator = loadUser(actor.getId(), "Creator not found");

        Department department = loadDepartment(request.getDepartmentId());

        List<User> assignees = request.getPositionId() ==null ? userRepository.findByDepartmentIdAndActiveTrue(department.getId())
                : userRepository.findByDepartmentIdAndActiveTrue(department.getId());

        if (assignees.isEmpty()) {
            throw new ResourceNotFoundException("No active users found for the given department/position");
        }


        List<Task> tasks = new ArrayList<>(assignees.size());

        for (User assignee : assignees) {
            Task task = taskMapper.toEntity(toCreateRequest(request));
            task.setAssignedBy(creator);
            task.setStatus(TaskStatus.TODO);
            task.setTaskCode(generateTaskCode());
            task.setTags(request.getTags() !=null ?new HashSet<>(request.getTags()) : null);
            taskAuthorizationService.assertCanAssign(actor, task, assignee);
            task.setAssignee(assignee);
            tasks.add(task);


        }

        List<Task> saved = taskRepository.saveAll(tasks);
        log.info("{} tasks created for department {} (position {}) by {}",
                saved.size(), department.getId(), request.getPositionId(), creator.getId());

        saved.forEach(t -> publishAssigned(t, creator.getId(), creator.getFullName()));

        return saved.stream().map(taskMapper::toResponse).collect(Collectors.toList());
    }


    private CreateTaskRequest toCreateRequest(BulkCreateTaskRequest request) {
        return CreateTaskRequest.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(request.getPriority())
                .departmentId(request.getDepartmentId())
                .startDate(request.getStartDate())
                .dueDate(request.getDueDate())
                .estimatedHours(request.getEstimatedHours())
                .build();
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
        TaskStatus targetStatus = parseStatus(status);
        Task saved = applyStatusTransition(id, targetStatus, actor);
        return taskMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public TaskReviewResponse reviewTask(UUID id, ReviewTaskRequest request) {
        UserPrincipal actor = SecurityUtils.getCurrentUser();
        TaskStatus decision = request.getStatus();
        if (decision != TaskStatus.APPROVED && decision != TaskStatus.REJECTED) {
            throw new BadRequestException("Review decision must be APPROVED or REJECTED");
        }

        Task saved = applyStatusTransition(id, decision, actor);

        User reviewer = userRepository.findById(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Reviewer not found"));

        TaskReview review = taskReviewRepository.findByTaskId(id).orElseGet(TaskReview::new);
        review.setTask(saved);
        review.setReviewer(reviewer);
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setDecision(decision);
        TaskReview savedReview = taskReviewRepository.save(review);

        log.info("Task {} reviewed by {} — decision={} rating={}", id, actor.getId(), decision, request.getRating());

        return taskReviewMapper.toResponse(savedReview);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskReviewResponse getReview(UUID taskId, UserPrincipal userPrincipal) {
        UserPrincipal actor = resolveActor(userPrincipal);
        Task task = loadTask(taskId);
        taskAuthorizationService.assertCanView(actor, task);

        return taskReviewRepository.findByTaskId(taskId)
                .map(taskReviewMapper::toResponse)
                .orElse(null);
    }

    /** Validates and applies a status transition, saves the task, and fires the change-notification event. */
    private Task applyStatusTransition(UUID id, TaskStatus targetStatus, UserPrincipal actor) {
        Task task = loadTask(id);
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

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID taskId, UserPrincipal userPrincipal) {
        UserPrincipal actor = resolveActor(userPrincipal);
        Task task = loadTask(taskId);
        taskAuthorizationService.assertCanView(actor, task);
        TaskReview review = taskReviewRepository.findByTaskId(taskId).orElse(null);
        return taskMapper.toDetailResponse(task, review);
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

        List<UUID> taskIds = page.getContent().stream().map(Task::getId).toList();
        Map<UUID, TaskReview> reviewsByTaskId = taskReviewRepository.findByTaskIdIn(taskIds).stream()
                .collect(Collectors.toMap(r -> r.getTask().getId(), r -> r));
        Map<UUID, Integer> commentCountByTaskId = groupCountsByTaskId(taskCommentRepository.countGroupedByTaskIdIn(taskIds));
        Map<UUID, Integer> attachmentCountByTaskId = groupCountsByTaskId(attachmentRepository.countGroupedByTaskIdIn(taskIds));

        return PageResponse.from(page.map(task -> taskMapper.toResponse(
                task,
                reviewsByTaskId.get(task.getId()),
                commentCountByTaskId.getOrDefault(task.getId(), 0),
                attachmentCountByTaskId.getOrDefault(task.getId(), 0)
        )));
    }

    /** Collapses a `[taskId, count]` GROUP BY projection into a lookup map. */
    private Map<UUID, Integer> groupCountsByTaskId(List<Object[]> rows) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((UUID) row[0], ((Long) row[1]).intValue());
        }
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public KpiSummaryResponse getMyKpiSummary(UserPrincipal userPrincipal) {
        UUID userId = resolveActor(userPrincipal).getId();

        long totalTasks = taskRepository.countByAssigneeIdAndActiveTrue(userId);
        long completedTasks = taskRepository.countByAssigneeIdAndStatus(userId, TaskStatus.DONE);
        long inProgressTasks = taskRepository.countByAssigneeIdAndStatus(userId, TaskStatus.IN_PROGRESS);
        long overdueTasks = taskRepository.countOverdueForAssignee(userId, FINISHED_STATUSES);

        double kpi = totalTasks == 0
                ? 0.0
                : Math.round((completedTasks * 1000.0) / totalTasks) / 10.0;

        return KpiSummaryResponse.builder()
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .inProgressTasks(inProgressTasks)
                .overdueTasks(overdueTasks)
                .kpi(kpi)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ManagerAssignedKpiResponse getManagerAssignedKpiSummary(UserPrincipal userPrincipal) {
        UUID managerId = resolveActor(userPrincipal).getId();
        List<Task> assignedTasks = taskRepository.findByAssignedByIdAndActiveTrue(managerId);

        Map<TaskStatus, Long> statusCounts = assignedTasks.stream()
                .collect(Collectors.groupingBy(Task::getStatus, Collectors.counting()));

        LocalDate today = LocalDate.now();
        long overdueTasks = assignedTasks.stream()
                .filter(task -> task.getDueDate() != null
                        && task.getDueDate().isBefore(today)
                        && !FINISHED_STATUSES.contains(task.getStatus()))
                .count();

        return ManagerAssignedKpiResponse.builder()
                .totalTasks(assignedTasks.size())
                .pendingTasks(statusCounts.getOrDefault(TaskStatus.IN_PROGRESS, 0L))
                .todoTasks(statusCounts.getOrDefault(TaskStatus.TODO, 0L))
                .reviewTasks(statusCounts.getOrDefault(TaskStatus.IN_REVIEW, 0L))
                .doneTasks(statusCounts.getOrDefault(TaskStatus.DONE, 0L))
                .rejectedTasks(statusCounts.getOrDefault(TaskStatus.REJECTED, 0L))
                .overdueTasks(overdueTasks)
                .memberWorkload(buildAssignedWorkload(assignedTasks))
                .weeklyCompletions(buildAssignedWeeklyCompletions(assignedTasks))
                .topPerformers(buildAssignedTopPerformers(assignedTasks))
                .build();
    }

    /**
     * Per-assignee performance among tasks this manager assigned — same shape/ranking as
     * DashboardServiceImpl's team-scoped buildTopPerformers, just grouped by assignee
     * instead of iterating a fixed manager_id "direct reports" list.
     */
    private List<TeamAnalyticsResponse.MemberPerformance> buildAssignedTopPerformers(List<Task> assignedTasks) {
        Map<UUID, List<Task>> tasksByAssignee = assignedTasks.stream()
                .filter(task -> task.getAssignee() != null)
                .collect(Collectors.groupingBy(task -> task.getAssignee().getId()));

        LocalDate today = LocalDate.now();

        return tasksByAssignee.values().stream()
                .map(memberTasks -> {
                    User assignee = memberTasks.get(0).getAssignee();
                    long total = memberTasks.size();
                    long completed = memberTasks.stream()
                            .filter(task -> task.getStatus() == TaskStatus.DONE)
                            .count();
                    long overdue = memberTasks.stream()
                            .filter(task -> task.getDueDate() != null
                                    && task.getDueDate().isBefore(today)
                                    && !FINISHED_STATUSES.contains(task.getStatus()))
                            .count();

                    List<Task> onTimeEligible = memberTasks.stream()
                            .filter(task -> task.getStatus() == TaskStatus.DONE
                                    && task.getDueDate() != null
                                    && task.getCompletedAt() != null)
                            .toList();
                    long onTime = onTimeEligible.stream()
                            .filter(task -> !task.getCompletedAt().atZone(ZoneId.systemDefault()).toLocalDate()
                                    .isAfter(task.getDueDate()))
                            .count();

                    return TeamAnalyticsResponse.MemberPerformance.builder()
                            .memberId(assignee.getId())
                            .memberName(assignee.getFullName())
                            .avatarUrl(assignee.getAvatarUrl())
                            .positionName(assignee.getPosition() == null ? null : assignee.getPosition().getName())
                            .totalTasks(total)
                            .completedTasks(completed)
                            .overdueTasks(overdue)
                            .completionRate(rate(completed, total))
                            .onTimeRate(rate(onTime, onTimeEligible.size()))
                            .build();
                })
                .sorted(Comparator
                        .comparingDouble(TeamAnalyticsResponse.MemberPerformance::getCompletionRate).reversed()
                        .thenComparing(Comparator.comparingLong(TeamAnalyticsResponse.MemberPerformance::getTotalTasks).reversed()))
                .limit(10)
                .toList();
    }

    private double rate(long numerator, long denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return Math.round((numerator * 1000.0) / denominator) / 10.0;
    }

    /** Per-assignee task count among tasks this manager assigned, sorted by count desc. */
    private List<TeamAnalyticsResponse.MemberWorkload> buildAssignedWorkload(List<Task> assignedTasks) {
        Map<UUID, List<Task>> tasksByAssignee = assignedTasks.stream()
                .filter(task -> task.getAssignee() != null)
                .collect(Collectors.groupingBy(task -> task.getAssignee().getId()));

        return tasksByAssignee.values().stream()
                .map(memberTasks -> {
                    User assignee = memberTasks.get(0).getAssignee();
                    return TeamAnalyticsResponse.MemberWorkload.builder()
                            .memberId(assignee.getId())
                            .memberName(assignee.getFullName())
                            .avatarUrl(assignee.getAvatarUrl())
                            .taskCount(memberTasks.size())
                            .build();
                })
                .sorted(Comparator.comparingLong(TeamAnalyticsResponse.MemberWorkload::getTaskCount).reversed())
                .toList();
    }

    /** Rolling 7-day window ending today, zero-filled for days with no completions. */
    private List<TeamAnalyticsResponse.DailyCompletion> buildAssignedWeeklyCompletions(List<Task> assignedTasks) {
        LocalDate today = LocalDate.now();
        LocalDate windowStart = today.minusDays(6);

        Map<LocalDate, Long> countsByDay = assignedTasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.DONE && task.getCompletedAt() != null)
                .map(task -> task.getCompletedAt().atZone(ZoneId.systemDefault()).toLocalDate())
                .filter(date -> !date.isBefore(windowStart) && !date.isAfter(today))
                .collect(Collectors.groupingBy(date -> date, Collectors.counting()));

        List<TeamAnalyticsResponse.DailyCompletion> days = new ArrayList<>();
        for (LocalDate date = windowStart; !date.isAfter(today); date = date.plusDays(1)) {
            days.add(TeamAnalyticsResponse.DailyCompletion.builder()
                    .date(date)
                    .count(countsByDay.getOrDefault(date, 0L))
                    .build());
        }
        return days;
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
