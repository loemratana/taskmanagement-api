package com.ratana.task_management_api.service;

import com.ratana.task_management_api.dto.request.TaskCommentRequest;
import com.ratana.task_management_api.dto.response.TaskCommentResponse;
import com.ratana.task_management_api.security.UserPrincipal;

import java.util.List;
import java.util.UUID;

public interface TaskCommentService {

    TaskCommentResponse addComment(UUID taskId, TaskCommentRequest request, UserPrincipal actor);

    List<TaskCommentResponse> listComments(UUID taskId, UserPrincipal actor);
}
