package com.ratana.task_management_api.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import java.util.UUID;


@Getter
public class TaskAssignedEvent extends ApplicationEvent {

    private final UUID taskId;
    private final String taskTitle;
    private final UUID assigneeId;
    private final UUID assignedBy;
    private final String assignedByName;
    private final UUID departmentId;
    public TaskAssignedEvent(Object source,UUID taskId,
                             String taskTitle,
                             UUID assigneeId,
                             UUID assignedBy,
                             String assignedByName,
                             UUID departmentId) {

        super(source);
        this.taskId = taskId;
        this.taskTitle = taskTitle;
        this.assigneeId = assigneeId;
        this.assignedBy = assignedBy;
        this.assignedByName = assignedByName;
        this.departmentId = departmentId;
    }
}
