package com.ratana.task_management_api.repository;

import com.ratana.task_management_api.model.TaskAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, UUID> {
}
