package com.ratana.task_management_api.infrastructure.messaging;

import com.ratana.task_management_api.task.enums.Priority;
import com.ratana.task_management_api.task.enums.SubmissionFileKind;
import com.ratana.task_management_api.task.enums.SubmissionStatus;
import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.booking.entity.MeetingBooking;
import com.ratana.task_management_api.booking.entity.MeetingLocation;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.entity.TaskAssignment;
import com.ratana.task_management_api.task.entity.TaskSubmission;
import com.ratana.task_management_api.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

    private static final int TELEGRAM_CAPTION_LIMIT = 1024;

    public String forTaskAssigned(Task task) {
        String base = """
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
                        "%s",              // placeholder for description — filled below
                        taskLink(task));

        String desc = escape(Objects.requireNonNullElse(task.getDescription(), ""));
        int remaining = TELEGRAM_CAPTION_LIMIT - (base.length() - 2); // -2 for %s placeholder
        if (remaining < 0) remaining = 0;
        if (desc.length() > remaining) {
            desc = desc.substring(0, Math.max(0, remaining - 1)) + "…";
        }
        return base.formatted(desc);
    }

    public String forTaskSubmitted(TaskSubmission submission) {
        Task task = submission.getTask();
        long images = submission.getFiles().stream().filter(f -> f.getKind() == SubmissionFileKind.IMAGE).count();
        long files = submission.getFiles().size() - images;

        String attachments = submission.getFiles().stream()
                .map(f -> {
                    String name = escape(f.getOriginalName());

                    return f.getKind() == SubmissionFileKind.IMAGE
                            ? "🖼️ " + name
                            : "📎 " + name;
                })
                .collect(java.util.stream.Collectors.joining("\n"));

        if (attachments.isBlank()) {
            attachments = "No attachments.";
        }
        return """
        📋 <b>Task Submitted for Review</b>

        <b>Title:</b> %s
        <b>Submitted by:</b> %s
        <b>Department:</b> %s
        <b>Priority:</b> %s
        <b>Round:</b> %d
        <b>Attachments:</b> %d image(s), %d file(s)

        <b>Submission note:</b>
        %s
        %s
        🔗 <a href="%s">Open in TMS</a>"""
                .formatted(
                        escape(task.getTitle()),
                        escape(submission.getSubmittedBy().getFullName()),
                        escape(departmentName(task)),
                        priorityLabel(task.getPriority()),
                        submission.getRound(),
                        images,
                        files,
                        escape(truncate(submission.getNote(), "No note provided.")),
                        lateBlock(submission),
                        taskLink(task));
    }

    /** Empty for on-time submissions; otherwise a warning with the due date and the assignee's reason. */
    private String lateBlock(TaskSubmission submission) {
        if (!submission.isLate()) {
            return "";
        }
        Task task = submission.getTask();
        return """

        ⚠️ <b>Late submission</b> (due %s)
        <b>Reason:</b> %s
        """.formatted(
                task.getDueDate() != null ? task.getDueDate() : "—",
                escape(truncate(submission.getLateReason(), "No reason provided.")));
    }

    /** Posted as a reply to the submission's message once a reviewer approves or rejects it. */
    public String forTaskReviewed(TaskSubmission submission) {
        Task task = submission.getTask();
        boolean rejected = submission.getStatus() == SubmissionStatus.REJECTED;
        String reviewer = submission.getReviewedBy() != null ? submission.getReviewedBy().getFullName() : "—";
        return """
        %s <b>%s</b>

        <b>Title:</b> %s
        <b>Submitted by:</b> %s
        <b>Reviewed by:</b> %s
        <b>Round:</b> %d
        <b>Task status:</b> %s

        <b>Comment:</b>
        %s

        🔗 <a href="%s">Open in TMS</a>"""
                .formatted(
                        rejected ? "❌" : "✅",
                        rejected ? "Submission Rejected" : "Submission Approved",
                        escape(task.getTitle()),
                        escape(submission.getSubmittedBy().getFullName()),
                        escape(reviewer),
                        submission.getRound(),
                        statusLabel(task.getStatus()),
                        escape(truncate(submission.getReviewComment(), "No comment.")),
                        taskLink(task));
    }

    private String statusLabel(TaskStatus status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case DONE -> "✅ Done";
            case APPROVED -> "👍 Approved (waiting to be closed)";
            case REJECTED -> "↩️ Rejected — needs rework";
            default -> status.name();
        };
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
        return truncate(task.getDescription(), "No description provided.");
    }

    private String truncate(String text, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        String trimmed = text.strip();
        return trimmed.length() > DESCRIPTION_MAX_LENGTH
                ? trimmed.substring(0, DESCRIPTION_MAX_LENGTH) + "…"
                : trimmed;
    }

    /**
     * Department group announcement for a meeting invitation or cancellation.
     *
     * @param when     already formatted local date and time range
     * @param audience who was invited, e.g. "IT department, HR / Manager"
     */
    public String forMeeting(MeetingBooking booking, boolean cancelled, String when, String audience) {
        StringBuilder text = new StringBuilder()
                .append(cancelled ? "❌ <b>Meeting Cancelled</b>" : "📅 <b>New Meeting</b>")
                .append("\n\n<b>Title:</b> ").append(escape(booking.getTitle()))
                .append("\n<b>When:</b> ").append(escape(when))
                .append(meetingPlace(booking))
                .append("\n<b>Organizer:</b> ").append(escape(booking.getOrganizer().getFullName()))
                .append("\n<b>Invited:</b> ").append(escape(audience));
        if (cancelled) {
            if (StringUtils.hasText(booking.getCancelReason())) {
                text.append("\n<b>Reason:</b> ").append(escape(booking.getCancelReason()));
            }
        } else if (StringUtils.hasText(booking.getDescription())) {
            text.append("\n\n").append(escape(truncate(booking.getDescription(), "")));
        }
        return text.toString();
    }

    /** "Room: A", "Location: ABC Café, St. 271 + map link", or "Online: Join meeting" — plus the note. */
    private String meetingPlace(MeetingBooking booking) {
        MeetingLocation location = booking.getLocation();
        StringBuilder text = new StringBuilder();
        switch (booking.getLocationType()) {
            case ROOM -> text.append("\n<b>Room:</b> ").append(escape(booking.locationLabel()));
            case EXTERNAL -> {
                text.append("\n<b>Location:</b> ").append(escape(booking.locationLabel()));
                if (location != null && StringUtils.hasText(location.getAddress())) {
                    text.append(", ").append(escape(location.getAddress()));
                }
                if (location != null && location.mapUrl() != null) {
                    text.append("\n<a href=\"").append(escapeAttribute(location.mapUrl()))
                            .append("\">📍 Open in Google Maps</a>");
                }
            }
            case ONLINE -> {
                text.append("\n<b>Online:</b> ").append(escape(booking.locationLabel()));
                if (location != null && location.getMeetingUrl() != null) {
                    text.append("\n<a href=\"").append(escapeAttribute(location.getMeetingUrl()))
                            .append("\">🔗 Join meeting</a>");
                }
            }
        }
        if (location != null && StringUtils.hasText(location.getNote())) {
            text.append("\n<b>Note:</b> ").append(escape(location.getNote()));
        }
        return text.toString();
    }

    private String escapeAttribute(String value) {
        return escape(value).replace("\"", "&quot;");
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
