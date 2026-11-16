package com.ratana.task_management_api.task.service;

import com.ratana.task_management_api.task.dto.request.TaskCommentRequest;
import com.ratana.task_management_api.task.dto.response.TaskCommentResponse;
import com.ratana.task_management_api.task.enums.TaskAuditAction;
import com.ratana.task_management_api.task.event.TaskEvent;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.task.exception.TaskNotFoundException;
import com.ratana.task_management_api.task.mapper.TaskCommentMapper;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.entity.TaskComment;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.task.repository.TaskCommentRepository;
import com.ratana.task_management_api.task.repository.TaskRepository;
import com.ratana.task_management_api.user.repository.UserRepository;
import com.ratana.task_management_api.infrastructure.security.SecurityUtils;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskCommentServiceImpl implements TaskCommentService {

    private final TaskRepository taskRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final UserRepository userRepository;
    private final TaskCommentMapper taskCommentMapper;
    private final TaskAuthorizationService taskAuthorizationService;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskAuditService taskAuditService;

    @Override
    @Transactional
    public TaskCommentResponse addComment(UUID taskId, TaskCommentRequest request, UserPrincipal principal) {
        UserPrincipal actor = resolveActor(principal);
        Task task = loadTask(taskId);
        taskAuthorizationService.assertCanView(actor, task);

        User author = userRepository.findById(actor.getId())
                .orElseThrow(() -> {
                    log.warn("Comment rejected: author {} not found", actor.getId());
                    return new ResourceNotFoundException("User not found: " + actor.getId());
                });

        TaskComment comment = new TaskComment();
        comment.setTask(task);
        comment.setUser(author);
        comment.setContent(request.getContent());
        TaskComment saved = taskCommentRepository.save(comment);

        log.info("Comment {} added to task {} by {}", saved.getId(), taskId, actor.getId());
        taskAuditService.record(task, actor.getId(), TaskAuditAction.COMMENT_ADDED,
                TaskAuditService.values("commentId", saved.getId()));
        eventPublisher.publishEvent(TaskEvent.commentAdded(task, actor.getId(), author.getFullName(), saved.getContent()));

        return taskCommentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskCommentResponse> listComments(UUID taskId, UserPrincipal principal) {
        UserPrincipal actor = resolveActor(principal);
        Task task = loadTask(taskId);
        taskAuthorizationService.assertCanView(actor, task);

        log.debug("Listing comments of task {} for user {}", taskId, actor.getId());
        return taskCommentMapper.toResponseList(
                taskCommentRepository.findByTaskIdOrderByCreatedAtAsc(taskId));
    }

    private UserPrincipal resolveActor(UserPrincipal provided) {
        return provided != null ? provided : SecurityUtils.getCurrentUser();
    }

    /** Archived (is_active = false) tasks are treated as not found, as in TaskServiceImpl. */
    private Task loadTask(UUID id) {
        return taskRepository.findById(id)
                .filter(Task::isActive)
                .orElseThrow(() -> {
                    log.warn("Task not found: {}", id);
                    return new TaskNotFoundException("Task not found: " + id);
                });
    }
}
