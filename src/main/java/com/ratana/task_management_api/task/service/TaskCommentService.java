package com.ratana.task_management_api.task.service;

import com.ratana.task_management_api.task.dto.request.TaskCommentRequest;
import com.ratana.task_management_api.task.dto.response.TaskCommentResponse;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;

import java.util.List;
import java.util.UUID;

public interface TaskCommentService {

    TaskCommentResponse addComment(UUID taskId, TaskCommentRequest request, UserPrincipal actor);

    List<TaskCommentResponse> listComments(UUID taskId, UserPrincipal actor);
}
