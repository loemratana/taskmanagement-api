package com.ratana.task_management_api.task.service;

import com.ratana.task_management_api.task.workflow.TaskWorkflowService;

import com.ratana.task_management_api.file.config.AttachmentUploadPolicy;
import com.ratana.task_management_api.task.dto.request.ReviewSubmissionRequest;
import com.ratana.task_management_api.task.dto.request.SubmitWorkRequest;
import com.ratana.task_management_api.file.dto.response.FileUploadResponse;
import com.ratana.task_management_api.common.pagination.PageResponse;
import com.ratana.task_management_api.task.dto.response.SubmissionFileResponse;
import com.ratana.task_management_api.task.dto.response.TaskSubmissionResponse;
import com.ratana.task_management_api.task.enums.SubmissionFileKind;
import com.ratana.task_management_api.task.enums.SubmissionStatus;
import com.ratana.task_management_api.task.enums.TaskAuditAction;
import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.task.event.TaskEvent;
import com.ratana.task_management_api.common.exception.BadRequestException;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.task.exception.TaskNotFoundException;
import com.ratana.task_management_api.file.exception.UnsupportedFileTypeException;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.entity.TaskSubmission;
import com.ratana.task_management_api.task.entity.TaskSubmissionFile;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.task.repository.TaskRepository;
import com.ratana.task_management_api.task.repository.TaskSubmissionRepository;
import com.ratana.task_management_api.user.repository.UserRepository;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.infrastructure.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.beans.factory.annotation.Value;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskSubmissionServiceImpl implements TaskSubmissionService {

    private static final Duration URL_TTL = Duration.ofMinutes(30);

    private final TaskSubmissionRepository submissionRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final TaskWorkflowService taskWorkflowService;
    private final TaskAuthorizationService taskAuthorizationService;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskAuditService taskAuditService;
    private final TaskGroupService taskGroupService;
    private final Clock clock;

    /** Due dates are business-timezone dates; the JVM runs in UTC in Docker. Same setting as TaskMapper. */
    @Value("${app.reminder.default-timezone:Asia/Phnom_Penh}")
    private ZoneId businessZone = ZoneId.of("Asia/Phnom_Penh");

    @Override
    @Transactional
    public TaskSubmissionResponse submit(UUID taskId, SubmitWorkRequest request, UserPrincipal actor) {
        List<MultipartFile> imageList = request.imageList();
        List<MultipartFile> fileList = request.fileList();

        log.debug("User {} submitting work for task {}: {} images, {} files",
                actor.getId(), taskId, imageList.size(), fileList.size());
        // Count + "not empty" are checked by @Valid on the DTO; content types are checked here.
        validateImages(imageList);
        validateFiles(fileList);

        Task task = loadTask(taskId);
        String oldStatus = task.getStatus().name();

        // Auto-start: a TODO task (or a REJECTED one being reworked) moves to IN_PROGRESS first,
        // through the normal workflow checks, so one submit call takes it straight to review.
        if (task.getStatus() == TaskStatus.TODO || task.getStatus() == TaskStatus.REJECTED
                || task.getStatus() == TaskStatus.BLOCKED) {
            taskWorkflowService.assertCanChangeStatus(task, TaskStatus.IN_PROGRESS, actor);
            task.setStatus(TaskStatus.IN_PROGRESS);
            log.debug("Task {} auto-started ({} -> IN_PROGRESS) before submission", taskId, oldStatus);
        }
        // Assignee + IN_PROGRESS -> IN_REVIEW check (also blocks a second pending submission).
        taskWorkflowService.assertCanChangeStatus(task, TaskStatus.IN_REVIEW, actor);

        // Late = submitted after the due date (the due date itself is still on time). Checked before
        // any upload so a missing reason never leaves objects in storage.
        LocalDate today = LocalDate.ofInstant(clock.instant(), businessZone);
        boolean late = task.getDueDate() != null && today.isAfter(task.getDueDate());
        String lateReason = late ? request.trimmedLateReason() : null;
        if (late && lateReason == null) {
            log.warn("Submission rejected: task {} is past its due date {} and no late reason was given",
                    taskId, task.getDueDate());
            throw new BadRequestException(
                    "This task was due on %s. Please provide a reason for the late submission."
                            .formatted(task.getDueDate()));
        }

        TaskSubmission submission = TaskSubmission.builder()
                .task(task)
                .submittedBy(userRepository.getReferenceById(actor.getId()))
                .round(submissionRepository.findMaxRound(taskId) + 1)
                .note(request.trimmedNote())
                .late(late)
                .lateReason(lateReason)
                .build();
        submission = submissionRepository.saveAndFlush(submission);   // need the id for the directory

        String baseDir = "tasks/%s/submissions/%s".formatted(taskId, submission.getId());
        List<String> uploadedKeys = new ArrayList<>();
        registerRollbackCleanup(uploadedKeys);

        uploadAll(submission, imageList, baseDir + "/images", SubmissionFileKind.IMAGE, uploadedKeys);
        uploadAll(submission, fileList, baseDir + "/files", SubmissionFileKind.FILE, uploadedKeys);

        task.setStatus(TaskStatus.IN_REVIEW);
        taskRepository.save(task);
        taskGroupService.onMemberChanged(task, actor.getId());
        taskAuditService.record(task, actor.getId(), TaskAuditAction.WORK_SUBMITTED,
                TaskAuditService.values("status", oldStatus),
                TaskAuditService.values("status", TaskStatus.IN_REVIEW.name(),
                        "submissionId", submission.getId(), "round", submission.getRound(),
                        "images", imageList.size(), "files", fileList.size(),
                        "late", late, "lateReason", lateReason),
                late ? "Late submission: " + lateReason : null);

        log.info("Task {} submitted (round {}) by {} with {} images, {} files{}",
                taskId, submission.getRound(), actor.getId(), imageList.size(), fileList.size(),
                late ? " (late, due " + task.getDueDate() + ")" : "");

        eventPublisher.publishEvent(TaskEvent.submitted(
                task, actor.getId(), nameOf(actor), oldStatus, submission.getId(), lateReason));

        return toResponse(submission);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskSubmissionResponse> list(UUID taskId, UserPrincipal actor) {
        Task task = loadTask(taskId);
        taskAuthorizationService.assertCanView(actor, task);
        log.debug("Listing submissions of task {} for user {}", taskId, actor.getId());
        return submissionRepository.findByTaskIdOrderByRoundDesc(taskId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TaskSubmissionResponse> listMine(SubmissionStatus status, Pageable pageable,
                                                         UserPrincipal actor) {
        Page<TaskSubmission> page = status == null
                ? submissionRepository.findBySubmittedById(actor.getId(), pageable)
                : submissionRepository.findBySubmittedByIdAndStatus(actor.getId(), status, pageable);
        log.debug("Listed {} own submission(s) for user {} (status={})", page.getTotalElements(), actor.getId(), status);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TaskSubmissionResponse> listPendingReview(Pageable pageable, UserPrincipal actor) {
        Page<TaskSubmission> page;
        if (taskAuthorizationService.canReviewAll(actor)) {
            page = submissionRepository.findByStatusAndTaskActiveTrueAndSubmittedByIdNot(
                    SubmissionStatus.PENDING, actor.getId(), pageable);
        } else if (taskAuthorizationService.canReviewDepartment(actor)) {
            page = submissionRepository.findByStatusAndTaskDepartmentIdAndTaskActiveTrueAndSubmittedByIdNot(
                    SubmissionStatus.PENDING, actor.getDepartmentId(), actor.getId(), pageable);
        } else if (taskAuthorizationService.canReviewTeam(actor)) {
            page = submissionRepository.findByStatusForDirectReportsOf(
                    SubmissionStatus.PENDING, actor.getId(), pageable);
        } else {
            log.warn("Access denied: user {} may not list submissions for review", actor.getId());
            throw new AccessDeniedException("You do not have permission to review submissions.");
        }
        log.debug("Listed {} pending submission(s) for reviewer {}", page.getTotalElements(), actor.getId());
        return PageResponse.from(page.map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public TaskSubmissionResponse get(UUID taskId, UUID submissionId, UserPrincipal actor) {
        TaskSubmission submission = loadSubmission(taskId, submissionId);
        taskAuthorizationService.assertCanView(actor, submission.getTask());
        log.debug("Submission {} of task {} fetched by {}", submissionId, taskId, actor.getId());
        return toResponse(submission);
    }

    @Override
    @Transactional
    public TaskSubmissionResponse review(UUID taskId, UUID submissionId, ReviewSubmissionRequest request,
                                         UserPrincipal actor) {
        TaskSubmission submission = loadSubmission(taskId, submissionId);
        if (submission.getSubmittedBy().getId().equals(actor.getId())) {
            log.warn("Review rejected: user {} tried to review their own submission {}", actor.getId(), submissionId);
            throw new AccessDeniedException("You cannot review your own submission.");
        }
        if (submission.getStatus() != SubmissionStatus.PENDING) {
            log.warn("Review rejected: submission {} is already {}", submissionId, submission.getStatus());
            throw new BadRequestException("This submission has already been reviewed.");
        }
        if (request.decision() == ReviewSubmissionRequest.Decision.REJECTED
                && (request.comment() == null || request.comment().isBlank())) {
            log.warn("Review rejected: rejecting submission {} requires a comment", submissionId);
            throw new BadRequestException("A comment is required when rejecting a submission.");
        }

        Task task = submission.getTask();
        boolean approved = request.decision() == ReviewSubmissionRequest.Decision.APPROVED;
        TaskStatus target = approved ? TaskStatus.APPROVED : TaskStatus.REJECTED;
        taskWorkflowService.assertCanChangeStatus(task, target, actor);

        submission.setStatus(approved ? SubmissionStatus.APPROVED : SubmissionStatus.REJECTED);
        submission.setReviewedBy(userRepository.getReferenceById(actor.getId()));
        submission.setReviewComment(request.comment());
        submission.setReviewedAt(Instant.now());

        String oldStatus = task.getStatus().name();
        task.setStatus(target);

        // Approving closes the task in the same call when the reviewer may close it (APPROVED -> DONE).
        // A reviewer without close permission leaves it APPROVED for someone who can.
        if (approved && taskAuthorizationService.canClose(actor, task)) {
            taskWorkflowService.assertCanChangeStatus(task, TaskStatus.DONE, actor);
            task.setStatus(TaskStatus.DONE);
            target = TaskStatus.DONE;
        }
        taskRepository.save(task);
        taskGroupService.onMemberChanged(task, actor.getId());   // last approval auto-closes the group
        log.info("Submission {} of task {} {} by {}", submissionId, taskId, target, actor.getId());
        taskAuditService.record(task, actor.getId(), TaskAuditAction.SUBMISSION_REVIEWED,
                TaskAuditService.values("status", oldStatus),
                TaskAuditService.values("status", target.name(), "submissionId", submissionId,
                        "round", submission.getRound(), "decision", submission.getStatus().name()),
                request.comment());
        eventPublisher.publishEvent(TaskEvent.reviewed(
                task, actor.getId(), nameOf(actor), oldStatus, submissionId,
                submission.getSubmittedBy().getId(), request.comment()));

        return toResponse(submission);
    }

    private void uploadAll(TaskSubmission submission, List<MultipartFile> parts, String directory,
                           SubmissionFileKind kind, List<String> uploadedKeys) {
        for (MultipartFile part : parts) {
            FileUploadResponse uploaded = storageService.upload(part, directory);
            uploadedKeys.add(uploaded.getStorageKey());
            log.debug("Submission {} {} uploaded: key={}, size={}",
                    submission.getId(), kind, uploaded.getStorageKey(), uploaded.getSize());
            submission.addFile(TaskSubmissionFile.builder()
                    .kind(kind)
                    .storageKey(uploaded.getStorageKey())
                    .originalName(uploaded.getOriginalFileName() != null
                            ? uploaded.getOriginalFileName()
                            : uploaded.getFileName())
                    .contentType(uploaded.getContentType())
                    .fileSize(uploaded.getSize())
                    .build());
        }
    }

    /** If the transaction rolls back, remove objects already pushed to MinIO. */
    private void registerRollbackCleanup(List<String> uploadedKeys) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                log.warn("Submission transaction rolled back, removing {} uploaded object(s)", uploadedKeys.size());
                for (String key : uploadedKeys) {
                    try {
                        storageService.delete(key);
                    } catch (RuntimeException e) {
                        log.warn("Orphaned object left in storage after rollback: {}", key, e);
                    }
                }
            }
        });
    }

    private void validateImages(List<MultipartFile> images) {
        for (MultipartFile image : images) {
            String type = image.getContentType();
            if (!AttachmentUploadPolicy.isImage(image)) {
                log.warn("Submission rejected: '{}' is not an image ({})", image.getOriginalFilename(), type);
                throw new UnsupportedFileTypeException(
                        "'%s' is not an image.".formatted(image.getOriginalFilename()));
            }
        }
    }

    private void validateFiles(List<MultipartFile> files) {
        for (MultipartFile file : files) {
            if (!AttachmentUploadPolicy.isAllowed(file)) {
                log.warn("Submission rejected: file type {} is not allowed", file.getContentType());
                throw new UnsupportedFileTypeException(
                        "File type '%s' is not allowed.".formatted(file.getContentType()));
            }
        }
        // Size checks run again inside StorageService.upload(...)
    }

    /** Archived (is_active = false) tasks are treated as not found, as in TaskServiceImpl. */
    /** Full name for notification text; the principal's username is an email address. */
    private String nameOf(UserPrincipal actor) {
        return userRepository.findById(actor.getId()).map(User::getFullName).orElse(actor.getUsername());
    }

    private Task loadTask(UUID taskId) {
        return taskRepository.findById(taskId)
                .filter(Task::isActive)
                .orElseThrow(() -> {
                    log.warn("Task not found: {}", taskId);
                    return new TaskNotFoundException("Task not found: " + taskId);
                });
    }

    private TaskSubmission loadSubmission(UUID taskId, UUID submissionId) {
        // A submission on an archived task can no longer be viewed or reviewed.
        return submissionRepository.findByIdAndTaskId(submissionId, taskId)
                .filter(submission -> submission.getTask().isActive())
                .orElseThrow(() -> {
                    log.warn("Submission {} not found for task {}", submissionId, taskId);
                    return new ResourceNotFoundException("Submission not found: " + submissionId);
                });
    }

    private TaskSubmissionResponse toResponse(TaskSubmission s) {
        List<SubmissionFileResponse> images = new ArrayList<>();
        List<SubmissionFileResponse> files = new ArrayList<>();
        for (TaskSubmissionFile f : s.getFiles()) {
            SubmissionFileResponse dto = SubmissionFileResponse.builder()
                    .id(f.getId())
                    .kind(f.getKind())
                    .originalName(f.getOriginalName())
                    .contentType(f.getContentType())
                    .size(f.getFileSize())
                    .url(storageService.createSignedUrl(f.getStorageKey(), URL_TTL))
                    .build();
            (f.getKind() == SubmissionFileKind.IMAGE ? images : files).add(dto);
        }

        User submitter = s.getSubmittedBy();
        User reviewer = s.getReviewedBy();
        return TaskSubmissionResponse.builder()
                .id(s.getId())
                .taskId(s.getTask().getId())
                .taskTitle(s.getTask().getTitle())
                .taskStatus(s.getTask().getStatus().name())
                .round(s.getRound())
                .note(s.getNote())
                .late(s.isLate())
                .lateReason(s.getLateReason())
                .status(s.getStatus())
                .submittedById(submitter.getId())
                .submittedByName(submitter.getFullName())
                .submittedAt(s.getSubmittedAt())
                .reviewedById(reviewer != null ? reviewer.getId() : null)
                .reviewedByName(reviewer != null ? reviewer.getFullName() : null)
                .reviewComment(s.getReviewComment())
                .reviewedAt(s.getReviewedAt())
                .images(images)
                .files(files)
                .build();
    }
}
