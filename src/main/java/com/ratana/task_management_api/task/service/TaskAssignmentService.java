package com.ratana.task_management_api.task.service;

import com.ratana.task_management_api.task.enums.AssignmentStatus;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.entity.TaskAssignment;
import com.ratana.task_management_api.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class TaskAssignmentService {

    /**
     * Adds collaborators to the task's assignment list. Users already on the list, or the
     * primary assignee, are skipped. Callers load and authorize the users first; the
     * assignments are persisted through the Task cascade.
     */
    public List<TaskAssignment> assignUsers(Task task, List<User> users, User assignedBy) {
        List<TaskAssignment> newAssignments = new ArrayList<>();

        for (User user : users) {
            if (task.isAssignee(user.getId())) {
                log.debug("User {} already assigned to task {}, skipping", user.getId(), task.getId());
                continue;
            }

            TaskAssignment assignment = TaskAssignment.builder()
                    .task(task)
                    .user(user)
                    .assignedBy(assignedBy)
                    .status(AssignmentStatus.ASSIGNED)
                    .assignedAt(Instant.now())
                    .build();
            task.addAssignment(assignment);
            newAssignments.add(assignment);
        }

        log.info("Task {}: {} new assignment(s) added by {}", task.getId(), newAssignments.size(), assignedBy.getId());
        return newAssignments;
    }
}
