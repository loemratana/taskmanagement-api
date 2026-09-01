package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.dto.response.TaskAssignmentResponse;
import com.ratana.task_management_api.enums.AssignmentStatus;
import com.ratana.task_management_api.model.Task;
import com.ratana.task_management_api.model.TaskAssignment;
import com.ratana.task_management_api.model.User;
import com.ratana.task_management_api.repository.TaskAssignmentRepository;
import com.ratana.task_management_api.repository.UserRepository;
import com.ratana.task_management_api.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskAssignmentService {

    private final TaskAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    @Transactional
    public List<TaskAssignment> assignUsers(Task task, List<UUID> userIds, UserPrincipal actor) {
        User assignedBy = userRepository.findById(actor.getId()).orElseThrow();
        List<TaskAssignment> newAssignments = new ArrayList<>();

        for (UUID userId : userIds) {
            User user = userRepository.findById(userId).orElseThrow();
            // Check if already assigned
            boolean exists = task.getAssignments().stream()
                    .anyMatch(a -> a.getUser().getId().equals(userId));
            if (exists) continue;

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

        return newAssignments;
    }

    public TaskAssignmentResponse toResponse(TaskAssignment assignment) {
        return TaskAssignmentResponse.builder()
                .id(assignment.getId())
                .taskId(assignment.getTask().getId())
                .user(mapUser(assignment.getUser()))
                .assignedBy(mapUser(assignment.getAssignedBy()))
                .status(assignment.getStatus())
                .assignedAt(assignment.getAssignedAt())
                .build();
    }

    private TaskAssignmentResponse.UserRef mapUser(User user) {
        if (user == null) {
            return null;
        }
        return TaskAssignmentResponse.UserRef.builder()
                .id(user.getId())
                .name(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
}
