package com.ratana.task_management_api.repository;

import com.ratana.task_management_api.enums.TaskStatus;
import com.ratana.task_management_api.model.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> , JpaSpecificationExecutor<Task> {




    @EntityGraph(attributePaths = {"department","assignee", "creator", "parentTask","comments","attachments"})
  Optional<Task> findById(UUID id);

    @Query(value = "SELECT nextval('task_code_seq')", nativeQuery = true)
    long nextTaskCodeSequence();

    List<Task> findByParentTaskId(UUID parentTaskId);

    // Used to check if assignee is in a department (for TASK_ASSIGN_DEPARTMENT)
    boolean existsByAssigneeIdAndDepartmentId(UUID assigneeId, UUID departmentId);

    // My assigned tasks
    Page<Task> findByAssigneeId(UUID assigneeId, Pageable pageable);

    // Tasks I created
//    Page<Task> findByCreatedById(UUID createdById, Pageable pageable);

    // Department tasks (for managers)
    Page<Task> findByDepartmentId(UUID departmentId, Pageable pageable);

    // Sub-tasks
    Page<Task> findByParentTaskId(UUID parentTaskId, Pageable pageable);

    // Counts for dashboard
    long countByAssigneeIdAndStatus(UUID assigneeId, TaskStatus status);

    long countByAssigneeIdAndActiveTrue(UUID assigneeId);

    long countByStatus(TaskStatus status);

    @Query("SELECT t.status, COUNT(t) FROM Task t GROUP BY t.status")
    List<Object[]> countGroupedByStatus();

    @Query("SELECT t.department.id, COUNT(t) FROM Task t GROUP BY t.department.id")
    List<Object[]> countGroupedByDepartment();

    /**
     * "Overdue" = due date has passed and the task isn't in a state where
     * that no longer matters (finished or cancelled). REJECTED/BLOCKED stay
     * eligible since the employee still owes the work.
     */
    @Query("SELECT COUNT(t) FROM Task t WHERE t.dueDate < CURRENT_DATE AND t.status NOT IN :finishedStatuses")
    long countOverdue(@Param("finishedStatuses") List<TaskStatus> finishedStatuses);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.assignee.id = :assigneeId AND t.dueDate < CURRENT_DATE AND t.status NOT IN :finishedStatuses")
    long countOverdueForAssignee(@Param("assigneeId") UUID assigneeId, @Param("finishedStatuses") List<TaskStatus> finishedStatuses);

    /**
     * Manager KPI dashboard: every active task THIS user assigned (assignedBy), not their
     * direct-report "team" — managers here don't reliably have manager_id-linked direct
     * reports. One query, aggregated in Java (status breakdown, overdue, per-assignee
     * workload, weekly completions), same pattern as findByAssigneeIdInAndActiveTrue below.
     */
    @EntityGraph(attributePaths = {"assignee"})
    List<Task> findByAssignedByIdAndActiveTrue(UUID assignedById);

    @EntityGraph(attributePaths = {"department", "assignee", "creator", "assignedBy"})
    List<Task> findTop20ByOrderByCreatedAtDesc();

    /**
     * Reminder scheduler candidates: not archived, not in a terminal status, has someone to remind.
     */
    @EntityGraph(attributePaths = {"assignee", "assignedBy", "creator"})
    @Query("SELECT t FROM Task t WHERE t.active = true AND t.status NOT IN :terminalStatuses AND t.assignee IS NOT NULL")
    List<Task> findActiveNonTerminalWithAssignee(@Param("terminalStatuses") List<TaskStatus> terminalStatuses);

    /**
     * Every active task belonging to the given assignees — one query, aggregated in
     * Java (status breakdown, overdue, on-time rate, per-member workload, weekly
     * completions) since the team a manager can query is bounded (their direct reports).
     */
    @EntityGraph(attributePaths = {"assignee"})
    List<Task> findByAssigneeIdInAndActiveTrue(List<UUID> assigneeIds);
}
