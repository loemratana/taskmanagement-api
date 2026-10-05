package com.ratana.task_management_api.mapper;

import com.ratana.task_management_api.dto.response.TaskReviewResponse;
import com.ratana.task_management_api.model.TaskReview;
import org.springframework.stereotype.Component;

@Component
public class TaskReviewMapper {

    private final TaskMapper taskMapper;

    public TaskReviewMapper(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    public TaskReviewResponse toResponse(TaskReview review) {
        if (review == null) {
            return null;
        }
        return TaskReviewResponse.builder()
                .id(review.getId())
                .taskId(review.getTask().getId())
                .reviewer(taskMapper.mapUser(review.getReviewer()))
                .rating(review.getRating())
                .comment(review.getComment())
                .decision(review.getDecision())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
