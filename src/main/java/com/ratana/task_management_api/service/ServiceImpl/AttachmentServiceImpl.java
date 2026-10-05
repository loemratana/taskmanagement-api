package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.config.AttachmentUploadPolicy;
import com.ratana.task_management_api.dto.response.AttachmentResponse;
import com.ratana.task_management_api.dto.response.PageResponse;
import com.ratana.task_management_api.exception.AttachmentNotFoundException;
import com.ratana.task_management_api.exception.FileSizeExceededException;
import com.ratana.task_management_api.exception.TaskNotFoundException;
import com.ratana.task_management_api.exception.UnauthorizedTaskAccessException;
import com.ratana.task_management_api.exception.UnsupportedFileTypeException;
import com.ratana.task_management_api.mapper.AttachmentMapper;
import com.ratana.task_management_api.model.Attachment;
import com.ratana.task_management_api.model.Task;
import com.ratana.task_management_api.model.User;
import com.ratana.task_management_api.repository.AttachmentRepository;
import com.ratana.task_management_api.repository.TaskRepository;
import com.ratana.task_management_api.repository.UserRepository;
import com.ratana.task_management_api.security.SecurityUtils;
import com.ratana.task_management_api.security.UserPrincipal;
import com.ratana.task_management_api.service.AttachmentService;
import com.ratana.task_management_api.service.SupabaseStorageService;
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
import java.util.UUID;
import java.util.regex.Pattern;


@Service
@Slf4j
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[^a-zA-Z0-9._-]");
    private static final Duration DOWNLOAD_URL_TTL = Duration.ofSeconds(60);

    private final AttachmentRepository attachmentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final SupabaseStorageService storageService;
    private final TaskAuthorizationService taskAuthorizationService;
    private final AttachmentMapper mapper;

    @Override
    @Transactional
    public AttachmentResponse upload(UUID taskId, MultipartFile file) {

        UserPrincipal currentUser = SecurityUtils.getCurrentUser();
        validateFile(file);

        Task task = loadTask(taskId);
        taskAuthorizationService.assertCanView(currentUser, task);

        String storageKey = buildStorageKey(taskId, file.getOriginalFilename());

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file.", e);
        }

        storageService.upload(storageKey, content, file.getContentType());

        User uploader = userRepository.getReferenceById(currentUser.getId());

        Attachment attachment = Attachment.builder()
                .fileName(storageKey.substring(storageKey.lastIndexOf('/') + 1))
                .fileSize(file.getSize())
                .fileType(file.getContentType())
                .fileUrl(storageKey)
                .originalName(sanitizeOriginalName(file.getOriginalFilename()))
                .user(uploader)
                .task(task)
                .build();

        Attachment saved;
        try {
            saved = attachmentRepository.save(attachment);
        } catch (RuntimeException ex) {
            // Compensate: remove the now-orphaned object from storage if the DB write fails.
            log.error("DB save failed after successful storage upload, compensating delete. key={}", storageKey, ex);
            storageService.delete(storageKey);
            throw ex;
        }

        log.info("Attachment {} uploaded for task {} by user {}", saved.getId(), taskId, currentUser.getId());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AttachmentResponse> listForTask(UUID taskId, Pageable pageable) {
        UserPrincipal currentUser = SecurityUtils.getCurrentUser();
        Task task = loadTask(taskId);
        taskAuthorizationService.assertCanView(currentUser, task);

        Page<AttachmentResponse> page = attachmentRepository.findByTaskId(taskId, pageable)
                .map(mapper::toResponse);
        return PageResponse.from(page);
    }

    @Override
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
        assertCanDelete(currentUser, attachment);

        storageService.delete(attachment.getFileUrl());
        attachmentRepository.delete(attachment);

        log.info("Attachment {} deleted by user {}", id, currentUser.getId());

    }

    /** The uploader may remove their own file; otherwise the actor needs task-update rights. */
    private void assertCanDelete(UserPrincipal actor, Attachment attachment) {
        boolean isUploader = attachment.getUser() != null
                && attachment.getUser().getId().equals(actor.getId());
        if (isUploader) {
            return;
        }
        if (!taskAuthorizationService.canUpdateTask(actor, attachment.getTask())) {
            throw new UnauthorizedTaskAccessException("You do not have permission to delete this attachment.");
        }
    }

    /** Builds a collision-resistant, path-traversal-safe storage key: {taskId}/{uuid}-{safeName} */
    private String buildStorageKey(UUID taskId, String originalFilename) {
        String safeName = sanitizeOriginalName(originalFilename);
        return "%s/%s-%s".formatted(taskId, UUID.randomUUID(), safeName);
    }


    private String sanitizeOriginalName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "file";
        }
        String nameOnly = Paths.get(originalFilename).getFileName().toString();
        String sanitized = UNSAFE_FILENAME_CHARS.matcher(nameOnly).replaceAll("_");
        return sanitized.length() > 200 ? sanitized.substring(sanitized.length() - 200) : sanitized;
    }

    private Task loadTask(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found: " + taskId));
    }


    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new UnsupportedFileTypeException("Uploaded file is empty.");
        }
        if (file.getSize() > AttachmentUploadPolicy.MAX_FILE_SIZE_BYTES) {
            throw new FileSizeExceededException("File size exceeds the maximum allowed size of 20MB.");
        }
        if (!AttachmentUploadPolicy.isAllowed(file.getContentType())) {
            throw new UnsupportedFileTypeException(
                    "File type '%s' is not allowed.".formatted(file.getContentType()));
        }
    }

    private Attachment loadAttachment(UUID id) {
        return attachmentRepository.findById(id)
                .orElseThrow(() -> new AttachmentNotFoundException("Attachment not found: " + id));
    }
}
