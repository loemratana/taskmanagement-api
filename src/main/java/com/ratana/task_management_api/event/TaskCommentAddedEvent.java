package com.ratana.task_management_api.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;


@Getter
public class TaskCommentAddedEvent  extends ApplicationEvent {
    private final UUID taskId;
    private final String taskTitle;
    private final UUID recipientId;      // task assignee or creator
    private final UUID commenterId;
    private final String commenterName;
    private final String commentPreview;

    public TaskCommentAddedEvent(Object source,
                                 UUID taskId,
                                 String taskTitle,
                                 UUID recipientId,
                                 UUID commenterId,
                                 String commenterName,
                                 String commentPreview) {
        super(source);
        this.taskId = taskId;
        this.taskTitle = taskTitle;
        this.recipientId = recipientId;
        this.commenterId = commenterId;
        this.commenterName = commenterName;
        this.commentPreview = commentPreview;
    }
}
