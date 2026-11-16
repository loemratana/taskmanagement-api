package com.ratana.task_management_api.task.service;

import com.ratana.task_management_api.task.dto.request.ReviewSubmissionRequest;
import com.ratana.task_management_api.task.dto.request.SubmitWorkRequest;
import com.ratana.task_management_api.common.pagination.PageResponse;
import com.ratana.task_management_api.task.dto.response.TaskSubmissionResponse;
import com.ratana.task_management_api.task.enums.SubmissionStatus;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface TaskSubmissionService {

    TaskSubmissionResponse submit(UUID taskId, SubmitWorkRequest request, UserPrincipal actor);

    List<TaskSubmissionResponse> list(UUID taskId, UserPrincipal actor);

    /** Submissions made by the current user across all tasks; status is optional. */
    PageResponse<TaskSubmissionResponse> listMine(SubmissionStatus status, Pageable pageable, UserPrincipal actor);

    /** PENDING submissions the current user is allowed to review (all, or own department). */
    PageResponse<TaskSubmissionResponse> listPendingReview(Pageable pageable, UserPrincipal actor);

    TaskSubmissionResponse get(UUID taskId, UUID submissionId, UserPrincipal actor);

    TaskSubmissionResponse review(UUID taskId, UUID submissionId, ReviewSubmissionRequest request,
                                  UserPrincipal actor);
}
