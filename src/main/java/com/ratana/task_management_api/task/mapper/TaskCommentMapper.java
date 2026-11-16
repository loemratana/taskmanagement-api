package com.ratana.task_management_api.task.mapper;

import com.ratana.task_management_api.task.dto.response.TaskCommentResponse;
import com.ratana.task_management_api.task.entity.TaskComment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TaskCommentMapper {

    private final TaskMapper taskMapper;

    public TaskCommentMapper(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    public TaskCommentResponse toResponse(TaskComment comment) {
        if (comment == null) {
            return null;
        }
        return TaskCommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .author(taskMapper.mapUser(comment.getUser()))
                .createdAt(comment.getCreatedAt())
                .build();
    }

    public List<TaskCommentResponse> toResponseList(List<TaskComment> comments) {
        return comments.stream().map(this::toResponse).toList();
    }
}
