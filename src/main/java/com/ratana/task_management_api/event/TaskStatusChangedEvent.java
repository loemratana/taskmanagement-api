package com.ratana.task_management_api.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;


@Getter
public class TaskStatusChangedEvent extends ApplicationEvent {
    private final UUID taskId;
    private final String taskTitle;
    private final UUID recipientId;      // usually the assignee or creator
    private final String oldStatus;
    private final String newStatus;
    private final UUID changedBy;
    private final String changedByName;

    public TaskStatusChangedEvent(Object source,
                                  UUID taskId,
                                  String taskTitle,
                                  UUID recipientId,
                                  String oldStatus,
                                  String newStatus,
                                  UUID changedBy,
                                  String changedByName) {
        super(source);
        this.taskId = taskId;
        this.taskTitle = taskTitle;
        this.recipientId = recipientId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.changedByName = changedByName;
    }
}
