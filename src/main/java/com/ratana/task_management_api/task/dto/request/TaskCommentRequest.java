package com.ratana.task_management_api.task.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskCommentRequest {

    @NotBlank(message = "Comment content is required")
    @Size(max = 4000, message = "Comment must be at most 4000 characters")
    private String content;
}
