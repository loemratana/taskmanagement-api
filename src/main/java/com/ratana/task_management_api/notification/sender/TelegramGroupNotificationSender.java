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
import org.springframework.transaction.support.TransactionOperations;

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
    /** Boot's TransactionTemplate; wraps only the short read in {@link #prepare}. */
    private final TransactionOperations transactions;

    private record LoadedFiles(List<TelegramFile> photos, List<TelegramFile> documents) {}

    /** What to post, decided from the database. */
    private enum Kind { ASSIGNMENT, SUBMISSION, REVIEW }

    /** A submission file to forward: metadata only, the bytes are downloaded outside the transaction. */
    private record FileRef(String name, String contentType, String storageKey, boolean photo) {}

    /** Everything needed to post, built inside a short read-only transaction; plain values, no entities. */
    private record Post(Kind kind, UUID taskId, UUID submissionId, UUID groupId, UUID departmentId,
                        String chatId, String text, Long replyTo, List<FileRef> files) {}

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.TELEGRAM_GROUP;
    }

    /**
     * Two steps, so no DB connection is held during Telegram or storage I/O: {@link #prepare} reads
     * the task, submission and group (lazy associations included) in a short transaction, then
     * {@link #deliver} downloads files and posts with none open.
     */
    @Override
    public void send(Notification notification) {
        Post post = transactions.execute(status -> prepare(notification));
        deliver(notification, post);
    }

    private Post prepare(Notification notification) {
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
        Long taskMessage = messageInChat(chatId, task.getTelegramChatId(), task.getTelegramMessageId());

        if (submission != null && isReview(notification)) {
            // Reply to the submission's message; fall back to the assignment's if it wasn't posted here.
            Long replyTo = messageInChat(chatId, submission.getTelegramChatId(), submission.getTelegramMessageId());
            return new Post(Kind.REVIEW, taskId, submission.getId(), group.getId(), departmentId, chatId,
                    messageFormatter.forTaskReviewed(submission), replyTo != null ? replyTo : taskMessage, List.of());
        }
        if (submission != null) {
            List<FileRef> files = submission.getFiles().stream()
                    .map(f -> new FileRef(f.getOriginalName(), f.getContentType(), f.getStorageKey(), sendsAsPhoto(f)))
                    .toList();
            return new Post(Kind.SUBMISSION, taskId, submission.getId(), group.getId(), departmentId, chatId,
                    messageFormatter.forTaskSubmitted(submission), taskMessage, files);
        }
        // Assignment (or any other task event): a new message that later ones reply to.
        return new Post(Kind.ASSIGNMENT, taskId, null, group.getId(), departmentId, chatId,
                messageFormatter.forTaskAssigned(task), null, List.of());
    }

    private void deliver(Notification notification, Post post) {
        String chatId = post.chatId();
        try {
            switch (post.kind()) {
                case REVIEW -> telegramBotClient.sendMessage(chatId, post.text(), post.replyTo());
                case SUBMISSION -> {
                    Long messageId = deliverSubmission(post);
                    remember("submission", post.submissionId(), messageId,
                            () -> submissionRepository.saveTelegramMessage(post.submissionId(), chatId, messageId));
                }
                case ASSIGNMENT -> {
                    Long messageId = telegramBotClient.sendMessage(chatId, post.text(), null);
                    remember("task", post.taskId(), messageId,
                            () -> taskRepository.saveTelegramMessage(post.taskId(), chatId, messageId));
                }
                default -> throw new IllegalStateException("Unhandled Telegram post kind: " + post.kind());
            }
            log.info("Telegram notification {} for task {} sent to group {} (department {})",
                    notification.getId(), post.taskId(), post.groupId(), post.departmentId());
        } catch (TelegramApiException e) {
            // Only a failure of the main message lands here, so a permanent error means the chat itself
            // is unusable (bot kicked, chat deleted) - stop routing to it.
            if (!e.isRetryable()) {
                log.warn("Deactivating Telegram group {} for department {} after a permanent error: {}",
                        post.groupId(), post.departmentId(), e.getMessage());
                groupService.deactivate(post.groupId());
            }
            throw e;
        }
    }

    /**
     * Photos only and a short enough caption: one album with the caption on it.
     * Otherwise, or if the album is rejected: the caption as a text message, then the files as
     * replies to it. Returns the id of the message the review should reply to (album lead or text).
     */
    private Long deliverSubmission(Post post) {
        String chatId = post.chatId();
        String caption = post.text();
        UUID submissionId = post.submissionId();
        LoadedFiles files = loadFiles(submissionId, post.files());

        boolean canUseAlbum = !files.photos().isEmpty()
                && files.documents().isEmpty()
                && caption.length() <= CAPTION_LIMIT;

        if (canUseAlbum) {
            try {
                Long messageId = telegramBotClient.sendPhotos(chatId, files.photos(), caption, post.replyTo());
                log.info("Submission {}: sent {} photo(s) with caption", submissionId, files.photos().size());
                return messageId;
            } catch (TelegramApiException e) {
                // Often a problem with the media itself (e.g. a 400 for bad dimensions), not the chat,
                // so don't deactivate the group here. If the chat is really gone, the text message
                // below fails too and deliver() deactivates it.
                log.warn("Submission {}: album send failed, falling back to text + files: {}",
                        submissionId, e.getMessage());
            }
        }

        Long messageId = telegramBotClient.sendMessage(chatId, caption, post.replyTo());
        // Files reply to the submission text, so they read as part of it.
        sendFilesBestEffort(chatId, submissionId, files, messageId != null ? messageId : post.replyTo());
        return messageId;
    }

    private LoadedFiles loadFiles(UUID submissionId, List<FileRef> refs) {
        List<TelegramFile> photos = new ArrayList<>();
        List<TelegramFile> documents = new ArrayList<>();
        for (FileRef file : refs) {
            try {
                TelegramFile telegramFile = new TelegramFile(
                        file.name(), file.contentType(), storageService.download(file.storageKey()));
                (file.photo() ? photos : documents).add(telegramFile);
            } catch (RuntimeException e) {
                log.warn("Submission {}: could not read '{}' from storage for Telegram: {}",
                        submissionId, file.name(), e.getMessage());
            }
        }
        return new LoadedFiles(photos, documents);
    }

    /**
     * Best effort: the message already went out, so a failed file is logged rather than thrown -
     * throwing would make the retry scheduler post the whole message again.
     */
    private void sendFilesBestEffort(String chatId, UUID submissionId, LoadedFiles files, Long replyTo) {
        if (!files.photos().isEmpty()) {
            try {
                telegramBotClient.sendPhotos(chatId, files.photos(), null, replyTo);
            } catch (TelegramApiException e) {
                log.warn("Submission {}: sending {} photo(s) to Telegram failed: {}",
                        submissionId, files.photos().size(), e.getMessage());
            }
        }
        for (TelegramFile document : files.documents()) {
            try {
                telegramBotClient.sendDocument(chatId, document, replyTo);
            } catch (TelegramApiException e) {
                log.warn("Submission {}: sending '{}' to Telegram failed: {}",
                        submissionId, document.fileName(), e.getMessage());
            }
        }
        log.info("Submission {}: forwarded {} photo(s) and {} document(s) to Telegram",
                submissionId, files.photos().size(), files.documents().size());
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
