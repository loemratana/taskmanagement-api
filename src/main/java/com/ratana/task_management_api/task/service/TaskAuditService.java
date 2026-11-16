package com.ratana.task_management_api.task.service;

import com.ratana.task_management_api.task.dto.response.TaskLogResponse;
import com.ratana.task_management_api.task.enums.TaskAuditAction;
import com.ratana.task_management_api.task.exception.TaskNotFoundException;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.entity.TaskLog;
import com.ratana.task_management_api.task.repository.TaskLogRepository;
import com.ratana.task_management_api.task.repository.TaskRepository;
import com.ratana.task_management_api.user.repository.UserRepository;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only task audit trail (task_logs). {@link #record} joins the caller's transaction on
 * purpose: the log row commits or rolls back together with the change it describes.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskAuditService {

    private static final int MAX_DESCRIPTION_LENGTH = 255;

    private final TaskLogRepository taskLogRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskAuthorizationService taskAuthorizationService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Task task, UUID actorId, TaskAuditAction action,
                       Map<String, Object> oldValue, Map<String, Object> newValue, String description) {
        TaskLog entry = new TaskLog();
        entry.setTask(task);
        entry.setUser(actorId != null ? userRepository.getReferenceById(actorId) : null);
        entry.setActionType(action.name());
        entry.setOldValue(oldValue);
        entry.setNewValue(newValue);
        entry.setDescription(truncate(description));
        entry.setIpAddress(currentClientIp());
        taskLogRepository.save(entry);
        log.debug("Audit {} on task {} by {}", action, task.getId(), actorId);
    }

    public void record(Task task, UUID actorId, TaskAuditAction action, Map<String, Object> newValue) {
        record(task, actorId, action, null, newValue, null);
    }

    @Transactional(readOnly = true)
    public List<TaskLogResponse> history(UUID taskId, UserPrincipal actor) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found: " + taskId));
        taskAuthorizationService.assertCanView(actor, task);
        return taskLogRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(entry -> TaskLogResponse.builder()
                        .id(entry.getId())
                        .action(entry.getActionType())
                        .actorId(entry.getUser() != null ? entry.getUser().getId() : null)
                        .actorName(entry.getUser() != null ? entry.getUser().getFullName() : null)
                        .oldValue(entry.getOldValue())
                        .newValue(entry.getNewValue())
                        .description(entry.getDescription())
                        .createdAt(entry.getCreatedAt())
                        .build())
                .toList();
    }

    /** Ordered key/value map that, unlike {@code Map.of}, accepts null values (e.g. "no assignee"). */
    public static Map<String, Object> values(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            map.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return map;
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > MAX_DESCRIPTION_LENGTH ? text.substring(0, MAX_DESCRIPTION_LENGTH) : text;
    }

    /** Behind a reverse proxy this is the proxy address unless forward headers are enabled (DEF-40). */
    private static String currentClientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String ip = request.getRemoteAddr();
            return ip != null && ip.length() > 45 ? ip.substring(0, 45) : ip;
        }
        return null;   // scheduler / async thread
    }
}
