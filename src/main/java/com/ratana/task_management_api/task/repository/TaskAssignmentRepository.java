package com.ratana.task_management_api.task.repository;

import com.ratana.task_management_api.task.entity.TaskAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, UUID> {
}
