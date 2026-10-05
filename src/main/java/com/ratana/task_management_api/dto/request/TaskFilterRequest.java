package com.ratana.task_management_api.dto.request;

import com.ratana.task_management_api.enums.Priority;
import com.ratana.task_management_api.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskFilterRequest {
    private TaskStatus taskStatus;
    private Priority priority;

    private UUID  departmentId;
    private UUID assigneeId;
    private UUID  createdById;
    private UUID assignedById;
    private String titleContains;
}
