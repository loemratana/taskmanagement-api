package com.ratana.task_management_api.notification.sender;

import com.ratana.task_management_api.notification.enums.NotificationChannel;
import com.ratana.task_management_api.notification.enums.NotificationType;
import com.ratana.task_management_api.task.enums.SubmissionFileKind;
import com.ratana.task_management_api.notification.exception.NotificationDeliveryException;
import com.ratana.task_management_api.infrastructure.messaging.TelegramApiException;
import com.ratana.task_management_api.organization.entity.DepartmentTelegramGroup;
import com.ratana.task_management_api.notification.entity.Notification;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.entity.TaskSubmission;
import com.ratana.task_management_api.task.entity.TaskSubmissionFile;
import com.ratana.task_management_api.task.repository.TaskRepository;
import com.ratana.task_management_api.task.repository.TaskSubmissionRepository;
import com.ratana.task_management_api.organization.service.DepartmentTelegramGroupService;
import com.ratana.task_management_api.infrastructure.storage.StorageService;
import com.ratana.task_management_api.infrastructure.messaging.TelegramBotClient;
import com.ratana.task_management_api.infrastructure.messaging.TelegramBotClient.TelegramFile;
import com.ratana.task_management_api.infrastructure.messaging.TelegramMessageFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Broadcasts a task notification to the task's department Telegram group, threaded as replies:
 * <ol>
 *   <li>assignment — a new message; its id is saved on the task</li>
 *   <li>submission (with its images and files) — a reply to the assignment message; its id is
 *       saved on the submission</li>
 *   <li>review (approved / rejected) — a reply to the submission message</li>
 * </ol>
 * A reply is only made inside the same chat, so if the department's group was changed in between
 * the message is simply posted without one. Routes off Task.department (stable regardless of who
 * is currently assigned), not the assignee's department.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramGroupNotificationSender implements NotificationSender {

    /** Telegram only shows JPEG/PNG/WebP up to 10MB as photos; anything else goes as a document. */
    private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;
    /** Telegram's limit for a media caption. */
    private static final int CAPTION_LIMIT = 1024;

    private final TaskRepository taskRepository;
    private final TaskSubmissionRepository submissionRepository;
    private final StorageService storageService;
    private final DepartmentTelegramGroupService groupService;
    private final TelegramMessageFormatter messageFormatter;
    private final TelegramBotClient telegramBotClient;

    private record LoadedFiles(List<TelegramFile> photos, List<TelegramFile> documents) {}

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.TELEGRAM_GROUP;
    }

    @Override
    public void send(Notification notification) {
        UUID taskId = notification.getReferenceId();
        if (taskId == null) {
            log.warn("Telegram notification {} has no task reference", notification.getId());
            throw new NotificationDeliveryException(
                    "TELEGRAM_GROUP notification " + notification.getId() + " has no task reference");
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> {
                    log.warn("Telegram notification {} failed: task {} not found", notification.getId(), taskId);
                    return new NotificationDeliveryException("Task not found: " + taskId);
                });

        UUID departmentId = task.getDepartment().getId();
        DepartmentTelegramGroup group = groupService.resolveActive(departmentId)
                .orElseThrow(() -> {
                    log.warn("Telegram notification {} failed: no active group for department {}",
                            notification.getId(), departmentId);
                    return new NotificationDeliveryException(
                            "No active Telegram group configured for department " + departmentId);
                });

        TaskSubmission submission = findSubmission(notification, taskId);
        String chatId = group.getChatId();

        try {
            if (submission != null && isReview(notification)) {
                // Reply to the submission's message; fall back to the assignment's if it wasn't posted here.
                Long replyTo = messageInChat(chatId, submission.getTelegramChatId(), submission.getTelegramMessageId());
                if (replyTo == null) {
                    replyTo = messageInChat(chatId, task.getTelegramChatId(), task.getTelegramMessageId());
                }
                telegramBotClient.sendMessage(chatId, messageFormatter.forTaskReviewed(submission), replyTo);
            } else if (submission != null) {
                Long replyTo = messageInChat(chatId, task.getTelegramChatId(), task.getTelegramMessageId());
                Long messageId = deliverSubmission(chatId, submission,
                        messageFormatter.forTaskSubmitted(submission), replyTo);
                remember("submission", submission.getId(), messageId,
                        () -> submissionRepository.saveTelegramMessage(submission.getId(), chatId, messageId));
            } else {
                // Assignment (or any other task event): a new message that later ones reply to.
                Long messageId = telegramBotClient.sendMessage(chatId, messageFormatter.forTaskAssigned(task), null);
                remember("task", taskId, messageId,
                        () -> taskRepository.saveTelegramMessage(taskId, chatId, messageId));
            }
            log.info("Telegram notification {} for task {} sent to group {} (department {})",
                    notification.getId(), taskId, group.getId(), departmentId);
        } catch (TelegramApiException e) {
            // Only a failure of the main message lands here, so a permanent error means the chat itself
            // is unusable (bot kicked, chat deleted) — stop routing to it.
            if (!e.isRetryable()) {
                log.warn("Deactivating Telegram group {} for department {} after a permanent error: {}",
                        group.getId(), departmentId, e.getMessage());
                groupService.deactivate(group.getId());
            }
            throw e;
        }
    }

    /**
     * Photos only and a short enough caption: one album with the caption on it.
     * Otherwise, or if the album is rejected: the caption as a text message, then the files as
     * replies to it. Returns the id of the message the review should reply to (album lead or text).
     */
    private Long deliverSubmission(String chatId, TaskSubmission submission, String caption, Long replyTo) {
        LoadedFiles files = loadFiles(submission);

        boolean canUseAlbum = !files.photos().isEmpty()
                && files.documents().isEmpty()
                && caption.length() <= CAPTION_LIMIT;

        if (canUseAlbum) {
            try {
                Long messageId = telegramBotClient.sendPhotos(chatId, files.photos(), caption, replyTo);
                log.info("Submission {}: sent {} photo(s) with caption", submission.getId(), files.photos().size());
                return messageId;
            } catch (TelegramApiException e) {
                // Often a problem with the media itself (e.g. a 400 for bad dimensions), not the chat,
                // so don't deactivate the group here. If the chat is really gone, the text message
                // below fails too and send() deactivates it.
                log.warn("Submission {}: album send failed, falling back to text + files: {}",
                        submission.getId(), e.getMessage());
            }
        }

        Long messageId = telegramBotClient.sendMessage(chatId, caption, replyTo);
        // Files reply to the submission text, so they read as part of it.
        sendFilesBestEffort(chatId, submission, files, messageId != null ? messageId : replyTo);
        return messageId;
    }

    private LoadedFiles loadFiles(TaskSubmission submission) {
        List<TelegramFile> photos = new ArrayList<>();
        List<TelegramFile> documents = new ArrayList<>();
        for (TaskSubmissionFile file : submission.getFiles()) {
            try {
                TelegramFile telegramFile = new TelegramFile(
                        file.getOriginalName(), file.getContentType(), storageService.download(file.getStorageKey()));
                (sendsAsPhoto(file) ? photos : documents).add(telegramFile);
            } catch (RuntimeException e) {
                log.warn("Submission {}: could not read '{}' from storage for Telegram: {}",
                        submission.getId(), file.getOriginalName(), e.getMessage());
            }
        }
        return new LoadedFiles(photos, documents);
    }

    /**
     * Best effort: the message already went out, so a failed file is logged rather than thrown —
     * throwing would make the retry scheduler post the whole message again.
     */
    private void sendFilesBestEffort(String chatId, TaskSubmission submission, LoadedFiles files, Long replyTo) {
        if (!files.photos().isEmpty()) {
            try {
                telegramBotClient.sendPhotos(chatId, files.photos(), null, replyTo);
            } catch (TelegramApiException e) {
                log.warn("Submission {}: sending {} photo(s) to Telegram failed: {}",
                        submission.getId(), files.photos().size(), e.getMessage());
            }
        }
        for (TelegramFile document : files.documents()) {
            try {
                telegramBotClient.sendDocument(chatId, document, replyTo);
            } catch (TelegramApiException e) {
                log.warn("Submission {}: sending '{}' to Telegram failed: {}",
                        submission.getId(), document.fileName(), e.getMessage());
            }
        }
        log.info("Submission {}: forwarded {} photo(s) and {} document(s) to Telegram",
                submission.getId(), files.photos().size(), files.documents().size());
    }

    /** The submission behind a "submitted for review" notification, or null for other notifications. */
    private TaskSubmission findSubmission(Notification notification, UUID taskId) {
        Object submissionId = notification.getMetadata() != null
                ? notification.getMetadata().get("submissionId")
                : null;
        if (submissionId == null) {
            return null;
        }
        return submissionRepository.findByIdAndTaskId(UUID.fromString(submissionId.toString()), taskId)
                .orElseThrow(() -> {
                    log.warn("Telegram notification {} failed: submission {} not found",
                            notification.getId(), submissionId);
                    return new NotificationDeliveryException("Submission not found: " + submissionId);
                });
    }

    private static boolean isReview(Notification notification) {
        return notification.getType() == NotificationType.APPROVAL_APPROVED
                || notification.getType() == NotificationType.APPROVAL_REJECTED;
    }

    /** The stored message id, only when it was posted in the chat we're about to post to. */
    private static Long messageInChat(String chatId, String storedChatId, Long storedMessageId) {
        return storedMessageId != null && chatId.equals(storedChatId) ? storedMessageId : null;
    }

    /**
     * Saves a posted message's id for the next reply. Best effort: the message is already out, so a
     * failure here is logged rather than thrown — throwing would make the retry scheduler post it again.
     */
    private static void remember(String what, UUID id, Long messageId, Runnable save) {
        if (messageId == null) {
            log.warn("Telegram didn't return a message id for {} {}; the next message won't be a reply", what, id);
            return;
        }
        try {
            save.run();
        } catch (RuntimeException e) {
            log.warn("Could not save Telegram message id {} for {} {}: {}", messageId, what, id, e.getMessage());
        }
    }

    private static boolean sendsAsPhoto(TaskSubmissionFile file) {
        return file.getKind() == SubmissionFileKind.IMAGE
                && PHOTO_TYPES.contains(file.getContentType())
                && file.getFileSize() <= MAX_PHOTO_BYTES;
    }
}
