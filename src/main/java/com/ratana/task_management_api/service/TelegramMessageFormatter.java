package com.ratana.task_management_api.service;

import com.ratana.task_management_api.enums.Priority;
import com.ratana.task_management_api.model.Task;
import com.ratana.task_management_api.model.TaskAssignment;
import com.ratana.task_management_api.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Builds the HTML text posted to a department's Telegram group. Kept free of
 * Notification/NotificationDelivery concerns so it's testable with just a Task.
 */
@Component
public class TelegramMessageFormatter {

    private static final DateTimeFormatter DUE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final int DESCRIPTION_MAX_LENGTH = 500;

    private final String taskLinkBaseUrl;

    public TelegramMessageFormatter(@Value("${app.client-base-url:http://localhost:5173}") String taskLinkBaseUrl) {
        this.taskLinkBaseUrl = taskLinkBaseUrl;
    }

    public String forTaskAssigned(Task task) {
        return """
                📌 <b>New Task Assigned</b>

                <b>Title:</b> %s
                <b>Assigned to:</b> %s
                <b>Department:</b> %s
                <b>Priority:</b> %s
                <b>Due date:</b> %s

                <b>Description:</b>
                %s

                🔗 <a href="%s">Open in TMS</a>"""
                .formatted(
                        escape(task.getTitle()),
                        escape(assigneeNames(task)),
                        escape(departmentName(task)),
                        priorityLabel(task.getPriority()),
                        dueDate(task),
                        escape(truncatedDescription(task)),
                        taskLink(task));
    }

    private String assigneeNames(Task task) {
        if (task.getAssignee() != null) {
            return task.getAssignee().getFullName();
        }
        List<String> names = task.getAssignments() == null
                ? List.of()
                : task.getAssignments().stream()
                        .map(TaskAssignment::getUser)
                        .filter(Objects::nonNull)
                        .map(User::getFullName)
                        .collect(Collectors.toList());
        return names.isEmpty() ? "Unassigned" : String.join(", ", names);
    }

    private String departmentName(Task task) {
        return task.getDepartment() != null ? task.getDepartment().getName() : "Unknown";
    }

    private String priorityLabel(Priority priority) {
        if (priority == null) {
            return "—";
        }
        return switch (priority) {
            case LOW -> "🟢 LOW";
            case MEDIUM -> "🟡 MEDIUM";
            case HIGH -> "🟠 HIGH";
            case URGENT -> "🔴 URGENT";
        };
    }

    private String dueDate(Task task) {
        return task.getDueDate() != null ? task.getDueDate().format(DUE_DATE_FORMAT) : "No due date set";
    }

    private String truncatedDescription(Task task) {
        String description = task.getDescription();
        if (description == null || description.isBlank()) {
            return "No description provided.";
        }
        String trimmed = description.strip();
        return trimmed.length() > DESCRIPTION_MAX_LENGTH
                ? trimmed.substring(0, DESCRIPTION_MAX_LENGTH) + "…"
                : trimmed;
    }

    private String taskLink(Task task) {
        return taskLinkBaseUrl + "/tasks/" + task.getId();
    }

    /** Telegram HTML parse mode only requires escaping &, <, > — order matters, escape & first. */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
