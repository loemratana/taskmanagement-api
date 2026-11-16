package com.ratana.task_management_api.task.dto.response;


import com.ratana.task_management_api.task.enums.Priority;
import com.ratana.task_management_api.task.enums.TaskStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSummaryResponse {

    private UUID id;


    private String taskCode;


    private String title;


    private TaskStatus status;


    private Priority priority;


    private UserRef assignee;


    private LocalDateTime dueDate;


    private LocalDateTime createdAt;


    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserRef {

        private UUID id;

        private String name;

        private String avatarUrl;
    }
}
