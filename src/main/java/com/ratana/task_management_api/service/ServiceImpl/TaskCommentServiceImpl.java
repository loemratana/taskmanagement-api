package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.dto.request.TaskCommentRequest;
import com.ratana.task_management_api.dto.response.TaskCommentResponse;
import com.ratana.task_management_api.event.TaskCommentAddedEvent;
import com.ratana.task_management_api.exception.ResourceNotFoundException;
import com.ratana.task_management_api.exception.TaskNotFoundException;
import com.ratana.task_management_api.mapper.TaskCommentMapper;
import com.ratana.task_management_api.model.Task;
import com.ratana.task_management_api.model.TaskComment;
import com.ratana.task_management_api.model.User;
import com.ratana.task_management_api.repository.TaskCommentRepository;
import com.ratana.task_management_api.repository.TaskRepository;
import com.ratana.task_management_api.repository.UserRepository;
import com.ratana.task_management_api.security.SecurityUtils;
import com.ratana.task_management_api.security.UserPrincipal;
import com.ratana.task_management_api.service.TaskCommentService;
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

    private static final int PREVIEW_LENGTH = 140;

    private final TaskRepository taskRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final UserRepository userRepository;
    private final TaskCommentMapper taskCommentMapper;
    private final TaskAuthorizationService taskAuthorizationService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public TaskCommentResponse addComment(UUID taskId, TaskCommentRequest request, UserPrincipal principal) {
        UserPrincipal actor = resolveActor(principal);
        Task task = loadTask(taskId);
        taskAuthorizationService.assertCanView(actor, task);

        User author = userRepository.findById(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actor.getId()));

        TaskComment comment = new TaskComment();
        comment.setTask(task);
        comment.setUser(author);
        comment.setContent(request.getContent());
        TaskComment saved = taskCommentRepository.save(comment);

        log.info("Comment added to task {} by {}", taskId, actor.getId());
        publishCommentAdded(task, saved, actor);

        return taskCommentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskCommentResponse> listComments(UUID taskId, UserPrincipal principal) {
        UserPrincipal actor = resolveActor(principal);
        Task task = loadTask(taskId);
        taskAuthorizationService.assertCanView(actor, task);

        return taskCommentMapper.toResponseList(
                taskCommentRepository.findByTaskIdOrderByCreatedAtAsc(taskId));
    }

    private void publishCommentAdded(Task task, TaskComment comment, UserPrincipal actor) {
        UUID recipientId = resolveRecipient(task, actor.getId());
        if (recipientId == null) {
            return;
        }

        String preview = comment.getContent().length() > PREVIEW_LENGTH
                ? comment.getContent().substring(0, PREVIEW_LENGTH) + "…"
                : comment.getContent();

        eventPublisher.publishEvent(new TaskCommentAddedEvent(
                this,
                task.getId(),
                task.getTitle(),
                recipientId,
                actor.getId(),
                actor.getUsername(),
                preview
        ));
    }

    /** Notify the assignee if someone else commented, otherwise the creator. */
    private UUID resolveRecipient(Task task, UUID actorId) {
        if (task.getAssignee() != null && !task.getAssignee().getId().equals(actorId)) {
            return task.getAssignee().getId();
        }
        if (task.getCreator() != null && !task.getCreator().getId().equals(actorId)) {
            return task.getCreator().getId();
        }
        return null;
    }

    private UserPrincipal resolveActor(UserPrincipal provided) {
        return provided != null ? provided : SecurityUtils.getCurrentUser();
    }

    private Task loadTask(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found: " + id));
    }
}
