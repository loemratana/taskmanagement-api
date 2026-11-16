package com.ratana.task_management_api.task.service;

import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.task.dto.request.ReviewSubmissionRequest;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.entity.TaskSubmission;
import com.ratana.task_management_api.task.exception.TaskNotFoundException;
import com.ratana.task_management_api.task.repository.TaskRepository;
import com.ratana.task_management_api.task.repository.TaskSubmissionRepository;
import com.ratana.task_management_api.task.workflow.TaskWorkflowService;
import com.ratana.task_management_api.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Archived (soft-deleted) tasks must not accept work or reviews. */
@ExtendWith(MockitoExtension.class)
class TaskSubmissionArchivedTaskTest {

    @Mock private TaskSubmissionRepository submissionRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private TaskWorkflowService taskWorkflowService;

    @InjectMocks
    private TaskSubmissionServiceImpl service;

    private final UUID taskId = UUID.randomUUID();
    private final UUID submissionId = UUID.randomUUID();

    private Task archivedTask() {
        Task task = new Task();
        task.setId(taskId);
        task.setActive(false);
        return task;
    }

    @Test
    @DisplayName("Reviewing a submission of an archived task is refused as not found")
    void review_archivedTask_notFound() {
        User submitter = new User();
        submitter.setId(UUID.randomUUID());
        TaskSubmission submission = TaskSubmission.builder()
                .id(submissionId).task(archivedTask()).submittedBy(submitter).round(1).build();
        when(submissionRepository.findByIdAndTaskId(submissionId, taskId)).thenReturn(Optional.of(submission));

        UserPrincipal reviewer = mock(UserPrincipal.class);
        ReviewSubmissionRequest request =
                new ReviewSubmissionRequest(ReviewSubmissionRequest.Decision.APPROVED, null);

        assertThatThrownBy(() -> service.review(taskId, submissionId, request, reviewer))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(taskRepository, never()).save(any());
        verifyNoInteractions(taskWorkflowService);
    }

    @Test
    @DisplayName("Listing submissions of an archived task is refused as not found")
    void list_archivedTask_notFound() {
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(archivedTask()));

        assertThatThrownBy(() -> service.list(taskId, mock(UserPrincipal.class)))
                .isInstanceOf(TaskNotFoundException.class);
    }
}
