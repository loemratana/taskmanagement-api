package com.ratana.task_management_api.file.service;

import com.ratana.task_management_api.task.service.TaskAuditService;
import com.ratana.task_management_api.task.service.TaskAuthorizationService;

import com.ratana.task_management_api.file.config.AttachmentUploadPolicy;
import com.ratana.task_management_api.file.dto.response.AttachmentResponse;
import com.ratana.task_management_api.common.pagination.PageResponse;
import com.ratana.task_management_api.task.enums.TaskAuditAction;
import com.ratana.task_management_api.file.exception.AttachmentNotFoundException;
import com.ratana.task_management_api.file.exception.FileSizeExceededException;
import com.ratana.task_management_api.task.exception.TaskNotFoundException;
import com.ratana.task_management_api.task.exception.UnauthorizedTaskAccessException;
import com.ratana.task_management_api.file.exception.UnsupportedFileTypeException;
import com.ratana.task_management_api.file.mapper.AttachmentMapper;
import com.ratana.task_management_api.file.entity.Attachment;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.file.repository.AttachmentRepository;
import com.ratana.task_management_api.task.repository.TaskRepository;
import com.ratana.task_management_api.user.repository.UserRepository;
import com.ratana.task_management_api.infrastructure.security.SecurityUtils;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.infrastructure.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;


/**
 * Files attached to a task (separate from work submissions). Anyone who can view a task may
 * attach files to it, like commenting; the uploader or anyone who can update the task may
 * delete one. Objects live in private storage, so responses carry short-lived signed URLs.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[^a-zA-Z0-9._-]");
    /** Long enough for the task screen to show previews and open files after loading the list. */
    private static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(30);
    /** Keeps the storage key within the 255-char file_url column. */
    private static final int MAX_SAFE_NAME_LENGTH = 150;

    private final AttachmentRepository attachmentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final TaskAuthorizationService taskAuthorizationService;
    private final AttachmentMapper mapper;
    private final TaskAuditService taskAuditService;

    @Override
    @Transactional
    public AttachmentResponse upload(UUID taskId, MultipartFile file) {

        UserPrincipal currentUser = SecurityUtils.getCurrentUser();
        Task task = loadTask(taskId);
        taskAuthorizationService.assertCanView(currentUser, task);
        validateFile(file);

        log.debug("User {} uploading attachment to task {}: name={}, type={}, size={}",
                currentUser.getId(), taskId, file.getOriginalFilename(), file.getContentType(), file.getSize());

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read uploaded file {} for task {}", file.getOriginalFilename(), taskId, e);
            throw new UncheckedIOException("Failed to read uploaded file.", e);
        }

        String storageKey = buildStorageKey(taskId, file.getOriginalFilename());
        storageService.upload(storageKey, content, file.getContentType());

        User uploader = userRepository.getReferenceById(currentUser.getId());

        Attachment attachment = Attachment.builder()
                .fileName(storageKey.substring(storageKey.lastIndexOf('/') + 1))
                .fileSize(file.getSize())
                .fileType(file.getContentType())
                .fileUrl(storageKey)
                .originalName(originalName(file.getOriginalFilename()))
                .user(uploader)
                .task(task)
                .build();

        Attachment saved;
        try {
            saved = attachmentRepository.saveAndFlush(attachment);
        } catch (RuntimeException ex) {
            // Compensate: remove the now-orphaned object from storage if the DB write fails.
            log.error("DB save failed after successful storage upload, compensating delete. key={}", storageKey, ex);
            storageService.delete(storageKey);
            throw ex;
        }

        log.info("Attachment {} uploaded for task {} by user {}", saved.getId(), taskId, currentUser.getId());
        taskAuditService.record(task, currentUser.getId(), TaskAuditAction.ATTACHMENT_ADDED,
                TaskAuditService.values("attachmentId", saved.getId(), "fileName", saved.getOriginalName(),
                        "size", saved.getFileSize()));
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AttachmentResponse> listForTask(UUID taskId, Pageable pageable) {
        UserPrincipal currentUser = SecurityUtils.getCurrentUser();
        Task task = loadTask(taskId);
        taskAuthorizationService.assertCanView(currentUser, task);

        Page<Attachment> page = attachmentRepository.findByTaskId(taskId, pageable);
        log.debug("Listed {} attachment(s) of task {} for user {}", page.getNumberOfElements(), taskId, currentUser.getId());
        return PageResponse.from(page.map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public String download(UUID attachmentId) {
        UserPrincipal currentUser = SecurityUtils.getCurrentUser();
        Attachment attachment = loadAttachment(attachmentId);
        taskAuthorizationService.assertCanView(currentUser, attachment.getTask());

        log.info("Attachment {} download link issued to user {}", attachmentId, currentUser.getId());
        return storageService.createSignedUrl(attachment.getFileUrl(), DOWNLOAD_URL_TTL);
    }

    @Override
    @Transactional
    public void delete(UUID id) {

        UserPrincipal currentUser = SecurityUtils.getCurrentUser();
        Attachment attachment = loadAttachment(id);
        boolean isUploader = attachment.getUser() != null
                && Objects.equals(attachment.getUser().getId(), currentUser.getId());
        if (!isUploader && !taskAuthorizationService.canUpdateTask(currentUser, attachment.getTask())) {
            log.warn("Access denied: user {} may not delete attachment {}", currentUser.getId(), id);
            throw new UnauthorizedTaskAccessException("You do not have permission to delete this attachment.");
        }

        taskAuditService.record(attachment.getTask(), currentUser.getId(), TaskAuditAction.ATTACHMENT_DELETED,
                TaskAuditService.values("attachmentId", attachment.getId(), "fileName", attachment.getOriginalName()),
                null, null);
        attachmentRepository.delete(attachment);
        attachmentRepository.flush();
        // Storage last: if the DB delete fails the file is still there and the row still points to it.
        storageService.delete(attachment.getFileUrl());

        log.info("Attachment {} deleted by user {}", id, currentUser.getId());
    }

    private AttachmentResponse toResponse(Attachment attachment) {
        return mapper.toResponse(attachment,
                storageService.createSignedUrl(attachment.getFileUrl(), DOWNLOAD_URL_TTL));
    }

    /** Builds a collision-resistant, path-traversal-safe storage key: tasks/{taskId}/attachments/{uuid}-{safeName} */
    private String buildStorageKey(UUID taskId, String originalFilename) {
        String safeName = UNSAFE_FILENAME_CHARS.matcher(originalName(originalFilename)).replaceAll("_");
        if (safeName.length() > MAX_SAFE_NAME_LENGTH) {
            safeName = safeName.substring(safeName.length() - MAX_SAFE_NAME_LENGTH);
        }
        return "tasks/%s/attachments/%s-%s".formatted(taskId, UUID.randomUUID(), safeName);
    }

    /** The file's own name (no client path), kept readable for display; capped to the column size. */
    private String originalName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "file";
        }
        String nameOnly = Paths.get(originalFilename.replace('\\', '/')).getFileName().toString();
        return nameOnly.length() > 255 ? nameOnly.substring(nameOnly.length() - 255) : nameOnly;
    }

    private Task loadTask(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> {
                    log.warn("Task not found: {}", taskId);
                    return new TaskNotFoundException("Task not found: " + taskId);
                });
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            log.warn("Attachment rejected: file is empty");
            throw new UnsupportedFileTypeException("Uploaded file is empty.");
        }
        if (file.getSize() > AttachmentUploadPolicy.MAX_FILE_SIZE_BYTES) {
            log.warn("Attachment rejected: {} is {} bytes, over the limit", file.getOriginalFilename(), file.getSize());
            throw new FileSizeExceededException("File size exceeds the maximum allowed size of 20MB.");
        }
        if (!AttachmentUploadPolicy.isAllowed(file)) {
            log.warn("Attachment rejected: file type {} is not allowed", file.getContentType());
            throw new UnsupportedFileTypeException(
                    "File type '%s' is not allowed.".formatted(file.getContentType()));
        }
    }

    private Attachment loadAttachment(UUID id) {
        return attachmentRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Attachment not found: {}", id);
                    return new AttachmentNotFoundException("Attachment not found: " + id);
                });
    }
}
