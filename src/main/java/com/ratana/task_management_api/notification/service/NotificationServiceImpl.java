package com.ratana.task_management_api.notification.service;

import com.google.api.pathtemplate.ValidationException;
import com.ratana.task_management_api.notification.dto.request.NotificationRequest;
import com.ratana.task_management_api.notification.dto.response.NotificationResponse;
import com.ratana.task_management_api.notification.enums.DeliveryStatus;
import com.ratana.task_management_api.notification.enums.NotificationChannel;
import com.ratana.task_management_api.notification.enums.NotificationPriority;
import com.ratana.task_management_api.notification.enums.NotificationStatus;
import com.ratana.task_management_api.notification.enums.NotificationType;
import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.task.event.TaskEvent;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.notification.entity.Notification;
import com.ratana.task_management_api.notification.entity.NotificationDelivery;
import com.ratana.task_management_api.notification.repository.NotificationDeliveryRepository;
import com.ratana.task_management_api.notification.repository.NotificationRepository;
import com.ratana.task_management_api.organization.service.DepartmentTelegramGroupService;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int COMMENT_PREVIEW_LENGTH = 140;

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationPreferenceService preferenceService;
    private final NotificationDispatcher dispatcher;
    private final DepartmentTelegramGroupService departmentTelegramGroupService;
    private final UserRepository userRepository;

    /** Personal channels only: extra reviewers must not re-post a submission to the department Telegram group. */
    private static final Set<NotificationChannel> PERSONAL_CHANNELS = Set.of(
            NotificationChannel.IN_APP, NotificationChannel.PUSH, NotificationChannel.EMAIL, NotificationChannel.TELEGRAM);

    @Override
    @Transactional
    public NotificationResponse send(NotificationRequest request) {
        if (request.getRecipientId() == null) {
            log.warn("Notification rejected: recipient id is null (type={})", request.getType());
            throw new ValidationException("Recipient ID cannot be null.");
        }
        if (request.getSenderId() == null) {
            log.warn("Notification rejected: sender id is null (type={})", request.getType());
            throw new ValidationException("Sender ID cannot be null.");
        }

        log.info("Sending notification type={} recipient={} title={}",
                request.getType(), request.getRecipientId(), request.getTitle());

        Notification notification = Notification.builder()
                .recipientId(request.getRecipientId())
                .senderId(request.getSenderId())
                .title(request.getTitle())
                .message(request.getMessage())
                .type(request.getType())
                .priority(request.getPriority())
                .referenceType(request.getReferenceType())
                .referenceId(request.getReferenceId())
                .metadata(request.getMetadata() != null ? request.getMetadata() : new HashMap<>())
                .status(NotificationStatus.PENDING)
                .build();

        notification = notificationRepository.save(notification);

        List<NotificationChannel> enabledChannels = resolveEnabledChannels(
                request.getRecipientId(), request.getType());

        if (request.getForcedChannels() != null) {
            for (NotificationChannel channel : request.getForcedChannels()) {
                if (!enabledChannels.contains(channel)) {
                    enabledChannels.add(channel);
                }
            }
        }

        if (request.getAllowedChannels() != null) {
            enabledChannels.retainAll(request.getAllowedChannels());
        }

        for (NotificationChannel channel : enabledChannels) {
            deliveryRepository.save(NotificationDelivery.builder()
                    .notification(notification)
                    .channel(channel)
                    .status(DeliveryStatus.PENDING)
                    .attempts(0)
                    .build());
        }
        log.debug("Notification {} saved with deliveries on channels {}", notification.getId(), enabledChannels);

        // Dispatch only after this TX commits so async workers see persisted rows
        final UUID notificationId = notification.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.debug("Dispatching notification {} after commit", notificationId);
                notificationRepository.findById(notificationId)
                        .ifPresentOrElse(dispatcher::dispatch,
                                () -> log.warn("Notification {} vanished before dispatch", notificationId));
            }
        });

        return toResponse(notification);
    }

    /*
     * Task notification rules. @Transactional is required: send() is called on `this`
     * (bypassing its proxy) and registers an after-commit hook that needs an active transaction.
     */
    @Override
    @Transactional
    public void handleTaskEvent(TaskEvent event) {
        List<NotificationRequest> requests = new ArrayList<>();
        switch (event.type()) {
            case ASSIGNED -> requests.add(taskAssigned(event));
            case COMMENT_ADDED -> requests.addAll(commentAdded(event));
            case STATUS_CHANGED -> requests.addAll(statusChanged(event));
            case SUBMITTED -> requests.addAll(submitted(event));
            case DETAILS_CHANGED -> requests.addAll(detailsChanged(event));
            case REVIEWED -> {
                NotificationRequest main = reviewed(event);
                requests.add(main);
                requests.addAll(reviewedOtherAssignees(event, main != null ? main.getRecipientId() : null));
            }
            case COLLABORATOR_ADDED -> requests.addAll(collaboratorsAdded(event));
            default -> log.debug("No notification rule for task event {}", event.type());
        }
        requests.removeIf(Objects::isNull);

        if (requests.isEmpty()) {
            log.debug("No recipient for task event | type={} taskId={} actorId={}",
                    event.type(), event.taskId(), event.actorId());
            return;
        }

        for (NotificationRequest request : requests) {
            send(request);
            log.info("Task notification queued | type={} taskId={} actorId={} recipientId={} channels={}",
                    event.type(), event.taskId(), event.actorId(), request.getRecipientId(),
                    request.getForcedChannels() != null ? request.getForcedChannels() : "user preferences");
        }
    }

    /** Personal notice to each new collaborator; the task's own assignment was already broadcast. */
    private List<NotificationRequest> collaboratorsAdded(TaskEvent event) {
        return event.collaboratorIds().stream()
                .filter(userId -> !userId.equals(event.actorId()))
                .map(userId -> taskRequest(event, userId)
                        .title("Added to Task")
                        .message(event.actorName() + " added you to the task: " + event.taskTitle()
                                + ". Anyone on the task can submit the work.")
                        .type(NotificationType.TASK_ASSIGNED)
                        .priority(NotificationPriority.HIGH)
                        .metadata(Map.of(
                                "taskTitle", event.taskTitle(),
                                "assignedBy", event.actorId().toString(),
                                "role", "COLLABORATOR"
                        ))
                        .build())
                .toList();
    }

    /**
     * On a shared task the submitter gets the main review notification; everyone else on the task
     * (primary assignee and collaborators, never the reviewer) is told the outcome personally.
     */
    private List<NotificationRequest> reviewedOtherAssignees(TaskEvent event, UUID alreadyNotified) {
        Set<UUID> others = new LinkedHashSet<>();
        if (event.assigneeId() != null) {
            others.add(event.assigneeId());
        }
        others.addAll(event.collaboratorIds());
        others.remove(event.actorId());
        others.remove(event.submittedById());
        if (alreadyNotified != null) {
            others.remove(alreadyNotified);
        }

        boolean rejected = TaskStatus.REJECTED.name().equals(event.newStatus());
        String message = rejected
                ? event.actorName() + " rejected the submitted work on \"" + event.taskTitle() + "\""
                        + (event.commentContent() != null ? ": " + preview(event.commentContent()) : "")
                : event.actorName() + " approved the submitted work on \"" + event.taskTitle()
                        + "\". The task is complete for everyone.";
        return others.stream()
                .map(userId -> taskRequest(event, userId)
                        .title(rejected ? "Shared Task Rejected" : "Shared Task Completed")
                        .message(message)
                        .type(rejected ? NotificationType.APPROVAL_REJECTED : NotificationType.APPROVAL_APPROVED)
                        .priority(NotificationPriority.NORMAL)
                        .metadata(Map.of(
                                "submissionId", event.submissionId().toString(),
                                "decision", rejected ? "REJECTED" : "APPROVED",
                                "newStatus", event.newStatus()
                        ))
                        .build())
                .toList();
    }

    /**
     * Always notifies the assignee, and broadcasts to the department Telegram group when one is active.
     * Subtasks (e.g. each member of a whole-department group task) are not broadcast, so one group
     * task doesn't post one message per person.
     */
    private NotificationRequest taskAssigned(TaskEvent event) {
        Set<NotificationChannel> forcedChannels = event.departmentId() == null || event.parentTaskId() != null
                ? Set.of()
                : departmentTelegramGroupService.resolveActive(event.departmentId())
                        .map(group -> Set.of(NotificationChannel.TELEGRAM_GROUP))
                        .orElse(Set.of());

        return taskRequest(event, event.assigneeId())
                .title("New Task Assigned")
                .message(event.actorName() + " assigned you a new task: " + event.taskTitle())
                .type(NotificationType.TASK_ASSIGNED)
                .priority(NotificationPriority.HIGH)
                .metadata(Map.of(
                        "taskTitle", event.taskTitle(),
                        "assignedBy", event.actorId().toString()
                ))
                .forcedChannels(forcedChannels)
                .build();
    }

    /** Notifies the assignee, or the creator if the assignee wrote it — never the commenter. */
    /** Everyone on the task (assignee, collaborators, creator) except the comment's author. */
    private List<NotificationRequest> commentAdded(TaskEvent event) {
        Set<UUID> recipients = new LinkedHashSet<>();
        if (event.assigneeId() != null) {
            recipients.add(event.assigneeId());
        }
        recipients.addAll(event.collaboratorIds());
        if (event.creatorId() != null) {
            recipients.add(event.creatorId());
        }
        recipients.remove(event.actorId());

        String preview = preview(event.commentContent());
        return recipients.stream()
                .map(recipientId -> taskRequest(event, recipientId)
                        .title("New Comment on Task")
                        .message(event.actorName() + " commented on \"" + event.taskTitle() + "\": " + preview)
                        .type(NotificationType.TASK_COMMENTED)
                        .priority(NotificationPriority.NORMAL)
                        .metadata(Map.of("commentPreview", preview))
                        .build())
                .toList();
    }

    /**
     * Someone else moved the task: everyone on it is told, in words ("Sok cancelled …", "… reopened …").
     * The assignee moved it: only blocking and unblocking matter to others, so the task's creator and
     * the assignee's manager are told then, with the reason.
     */
    private List<NotificationRequest> statusChanged(TaskEvent event) {
        boolean byAssignee = event.actorId().equals(event.assigneeId())
                || event.collaboratorIds().contains(event.actorId());
        String reason = event.commentContent();
        boolean blocked = TaskStatus.BLOCKED.name().equals(event.newStatus());
        boolean unblocked = TaskStatus.BLOCKED.name().equals(event.oldStatus()) && !blocked;
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("oldStatus", event.oldStatus());
        metadata.put("newStatus", event.newStatus());
        if (reason != null) {
            metadata.put("reason", reason);
        }

        if (byAssignee) {
            if (!blocked && !unblocked) {
                return List.of();
            }
            String message = blocked
                    ? "🚫 " + event.actorName() + " blocked \"" + event.taskTitle() + "\""
                            + (reason != null ? ": " + preview(reason) : "")
                    : "✅ " + event.actorName() + " unblocked \"" + event.taskTitle() + "\" and is working on it again";
            return watchersOf(event).stream()
                    .map(userId -> taskRequest(event, userId)
                            .title(blocked ? "Task Blocked" : "Task Unblocked")
                            .message(message)
                            .type(NotificationType.TASK_UPDATED)
                            .priority(blocked ? NotificationPriority.HIGH : NotificationPriority.NORMAL)
                            .metadata(metadata)
                            .build())
                    .toList();
        }

        boolean reopened = TaskStatus.TODO.name().equals(event.newStatus())
                && (TaskStatus.DONE.name().equals(event.oldStatus())
                        || TaskStatus.CANCELLED.name().equals(event.oldStatus()));
        String title;
        String message;
        if (TaskStatus.CANCELLED.name().equals(event.newStatus())) {
            title = "Task Cancelled";
            message = event.actorName() + " cancelled \"" + event.taskTitle() + "\"";
        } else if (reopened) {
            title = "Task Reopened";
            message = event.actorName() + " reopened \"" + event.taskTitle() + "\"";
        } else if (TaskStatus.DONE.name().equals(event.newStatus())) {
            title = "Task Closed";
            message = event.actorName() + " closed \"" + event.taskTitle() + "\"";
        } else {
            title = "Task Status Updated";
            message = event.actorName() + " moved \"" + event.taskTitle() + "\" from "
                    + statusLabel(event.oldStatus()) + " to " + statusLabel(event.newStatus());
        }
        String fullMessage = reason != null ? message + ": " + preview(reason) : message;
        return peopleOnTask(event).stream()
                .map(userId -> taskRequest(event, userId)
                        .title(title)
                        .message(fullMessage)
                        .type(NotificationType.TASK_UPDATED)
                        .priority(NotificationPriority.NORMAL)
                        .metadata(metadata)
                        .build())
                .toList();
    }

    /** The title, priority, due date or description changed: everyone working on the task is told how. */
    private List<NotificationRequest> detailsChanged(TaskEvent event) {
        String summary = event.commentContent();
        String icon = summary.startsWith("due date") ? "📅 " : "✏️ ";
        return peopleOnTask(event).stream()
                .map(userId -> taskRequest(event, userId)
                        .title("Task Updated")
                        .message(icon + event.actorName() + " changed \"" + event.taskTitle() + "\": " + summary)
                        .type(NotificationType.TASK_UPDATED)
                        .priority(NotificationPriority.NORMAL)
                        .metadata(Map.of("changes", summary))
                        .build())
                .toList();
    }

    /** Primary assignee and collaborators, minus whoever acted. */
    private static Set<UUID> peopleOnTask(TaskEvent event) {
        Set<UUID> people = new LinkedHashSet<>();
        if (event.assigneeId() != null) {
            people.add(event.assigneeId());
        }
        people.addAll(event.collaboratorIds());
        people.remove(event.actorId());
        return people;
    }

    /**
     * Who oversees the task: its creator and the primary assignee's direct manager (the person who
     * reviews a team member's work), minus whoever acted and the assignee themselves.
     */
    private Set<UUID> watchersOf(TaskEvent event) {
        Set<UUID> watchers = new LinkedHashSet<>();
        if (event.creatorId() != null) {
            watchers.add(event.creatorId());
        }
        UUID managerId = managerOf(event.assigneeId());
        if (managerId != null) {
            watchers.add(managerId);
        }
        watchers.remove(event.actorId());
        watchers.remove(event.assigneeId());
        return watchers;
    }

    private UUID managerOf(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(User::getManager)
                .map(User::getId)
                .orElse(null);
    }

    /** "IN_PROGRESS" → "In progress". */
    private static String statusLabel(String status) {
        if (status == null || status.isEmpty()) {
            return "none";
        }
        String spaced = status.replace('_', ' ').toLowerCase(java.util.Locale.ENGLISH);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /**
     * Notifies everyone who can review it — the task's creator and the assignee's direct manager — and
     * broadcasts the submission, with its images and files, to the department Telegram group when one is
     * active (once, on the first reviewer's notification). With no reviewer to notify, only the group is
     * told. A late submission says so in the title and message, and carries late/lateReason in metadata.
     */
    private List<NotificationRequest> submitted(TaskEvent event) {
        boolean groupActive = event.departmentId() != null
                && departmentTelegramGroupService.resolveActive(event.departmentId()).isPresent();
        List<UUID> reviewers = new ArrayList<>(watchersOf(event));
        reviewers.remove(event.submittedById());
        if (reviewers.isEmpty() && !groupActive) {
            return List.of();
        }

        boolean late = event.lateReason() != null;
        String message = event.actorName() + " submitted \"" + event.taskTitle() + "\" for review";
        if (late) {
            message += " after the due date. Reason: " + preview(event.lateReason());
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("submissionId", event.submissionId().toString());
        metadata.put("oldStatus", event.oldStatus());
        metadata.put("newStatus", event.newStatus());
        metadata.put("late", late);
        if (late) {
            metadata.put("lateReason", event.lateReason());
        }
        String title = late ? "Late Task Submitted for Review" : "Task Submitted for Review";

        if (reviewers.isEmpty()) {
            // Nobody to tell personally (e.g. the submitter created the task): only the group hears.
            return List.of(taskRequest(event, event.actorId())
                    .title(title).message(message).type(NotificationType.APPROVAL_REQUEST)
                    .priority(NotificationPriority.HIGH).metadata(metadata)
                    .forcedChannels(Set.of(NotificationChannel.TELEGRAM_GROUP))
                    .allowedChannels(Set.of(NotificationChannel.TELEGRAM_GROUP))
                    .build());
        }
        List<NotificationRequest> requests = new ArrayList<>();
        for (int i = 0; i < reviewers.size(); i++) {
            NotificationRequest.NotificationRequestBuilder builder = taskRequest(event, reviewers.get(i))
                    .title(title).message(message).type(NotificationType.APPROVAL_REQUEST)
                    .priority(NotificationPriority.HIGH).metadata(metadata);
            if (i == 0 && groupActive) {
                builder.forcedChannels(Set.of(NotificationChannel.TELEGRAM_GROUP));
            } else {
                builder.allowedChannels(PERSONAL_CHANNELS);
            }
            requests.add(builder.build());
        }
        return requests;
    }

    /**
     * Tells the assignee their submission was approved or rejected, and posts the decision to the
     * department Telegram group (as a reply to the submission's message) when one is active.
     */
    private NotificationRequest reviewed(TaskEvent event) {
        boolean groupActive = event.departmentId() != null
                && departmentTelegramGroupService.resolveActive(event.departmentId()).isPresent();
        // The person who did the work: the submitter (a collaborator on a shared task), else the assignee.
        UUID recipientId = firstOtherThanActor(event, event.submittedById(), event.assigneeId());
        if (recipientId == null && !groupActive) {
            return null;
        }

        boolean rejected = TaskStatus.REJECTED.name().equals(event.newStatus());
        String message = rejected
                ? event.actorName() + " rejected your work on \"" + event.taskTitle() + "\""
                        + (event.commentContent() != null ? ": " + preview(event.commentContent()) : "")
                : event.actorName() + " approved your work on \"" + event.taskTitle() + "\"";

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("submissionId", event.submissionId().toString());
        metadata.put("decision", rejected ? "REJECTED" : "APPROVED");
        metadata.put("oldStatus", event.oldStatus());
        metadata.put("newStatus", event.newStatus());

        NotificationRequest.NotificationRequestBuilder builder = taskRequest(
                event, recipientId != null ? recipientId : event.actorId())
                .title(rejected ? "Submission Rejected" : "Submission Approved")
                .message(message)
                .type(rejected ? NotificationType.APPROVAL_REJECTED : NotificationType.APPROVAL_APPROVED)
                .priority(rejected ? NotificationPriority.HIGH : NotificationPriority.NORMAL)
                .metadata(metadata)
                .forcedChannels(groupActive ? Set.of(NotificationChannel.TELEGRAM_GROUP) : Set.of());
        if (recipientId == null) {
            // Reviewer reviewed work on a task assigned to themselves: only the group is told.
            builder.allowedChannels(Set.of(NotificationChannel.TELEGRAM_GROUP));
        }
        return builder.build();
    }

    private static NotificationRequest.NotificationRequestBuilder taskRequest(TaskEvent event, UUID recipientId) {
        return NotificationRequest.builder()
                .recipientId(recipientId)
                .senderId(event.actorId())
                .referenceType("TASK")
                .referenceId(event.taskId());
    }

    /** First candidate that exists and is not the actor — users are never notified about their own actions. */
    private static UUID firstOtherThanActor(TaskEvent event, UUID... candidates) {
        for (UUID candidate : candidates) {
            if (candidate != null && !candidate.equals(event.actorId())) {
                return candidate;
            }
        }
        return null;
    }

    private static String preview(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > COMMENT_PREVIEW_LENGTH
                ? content.substring(0, COMMENT_PREVIEW_LENGTH) + "…"
                : content;
    }

    @Override
    public List<NotificationChannel> resolveEnabledChannels(UUID userId) {
        return resolveEnabledChannels(userId, null);
    }

    private List<NotificationChannel> resolveEnabledChannels(UUID userId, NotificationType type) {
        return new ArrayList<>(preferenceService.resolveEnabledChannels(userId, type));
    }

    @Override
    public Page<NotificationResponse> getMyNotifications(UUID currentUserId, Pageable pageable) {
        log.debug("Fetching notifications for user {} (page={}, size={})",
                currentUserId, pageable.getPageNumber(), pageable.getPageSize());
        return notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(currentUserId, pageable)
                .map(this::toResponse);
    }

    @Override
    public long getUnreadCount(UUID currentUserId) {
        log.debug("Counting unread notifications for user {}", currentUserId);
        return notificationRepository.countByRecipientIdAndIsReadFalse(currentUserId);
    }

    @Override
    @Transactional
    public void markAsRead(UUID notificationId, UUID currentUserId) {
        int updated = notificationRepository.markAsRead(notificationId, currentUserId);
        if (updated == 0) {
            assertOwned(notificationId, currentUserId);
            log.debug("Notification {} was already read by user {}", notificationId, currentUserId);
            return;
        }
        log.info("Notification {} marked as read by user {}", notificationId, currentUserId);
    }

    @Override
    @Transactional
    public void markAsUnread(UUID notificationId, UUID currentUserId) {
        int updated = notificationRepository.markAsUnread(notificationId, currentUserId);
        if (updated == 0) {
            assertOwned(notificationId, currentUserId);
            log.debug("Notification {} was already unread for user {}", notificationId, currentUserId);
            return;
        }
        log.info("Notification {} marked as unread by user {}", notificationId, currentUserId);
    }

    @Override
    @Transactional
    public int markAllAsRead(UUID currentUserId) {
        int updated = notificationRepository.markAllAsRead(currentUserId);
        log.info("{} notification(s) marked as read by user {}", updated, currentUserId);
        return updated;
    }

    @Override
    @Transactional
    public void delete(UUID notificationId, UUID currentUserId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, currentUserId)
                .orElseThrow(() -> notFound(notificationId, currentUserId));
        // Entity delete (not a bulk query) so the deliveries cascade with it.
        notificationRepository.delete(notification);
        log.info("Notification {} deleted by user {}", notificationId, currentUserId);
    }

    /** Recipients only: someone else's notification id looks the same as a missing one. */
    private void assertOwned(UUID notificationId, UUID currentUserId) {
        if (!notificationRepository.existsByIdAndRecipientId(notificationId, currentUserId)) {
            throw notFound(notificationId, currentUserId);
        }
    }

    private ResourceNotFoundException notFound(UUID notificationId, UUID currentUserId) {
        log.warn("Notification {} not found for user {}", notificationId, currentUserId);
        return new ResourceNotFoundException("Notification not found: " + notificationId);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .recipientId(n.getRecipientId())
                .senderId(n.getSenderId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .priority(n.getPriority())
                .status(n.getStatus())
                .isRead(n.isRead())
                .readAt(n.getReadAt())
                .referenceType(n.getReferenceType())
                .referenceId(n.getReferenceId())
                .metadata(n.getMetadata())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
