package com.ratana.task_management_api.task.repository;

import com.ratana.task_management_api.dashboard.dto.response.DashboardTaskStatistics;
import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.task.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task>, TaskCountRepository {




    @EntityGraph(attributePaths = {"department","assignee", "creator", "parentTask"})
    Optional<Task> findById(UUID id);

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

    long countByStatus(TaskStatus status);

    @Query("SELECT t.status, COUNT(t) FROM Task t GROUP BY t.status")
    List<Object[]> countGroupedByStatus();

    @Query("SELECT t.department.id, COUNT(t) FROM Task t GROUP BY t.department.id")
    List<Object[]> countGroupedByDepartment();

    @Query("""
       SELECT COUNT(t)
       FROM Task t
       WHERE t.dueDate < :now
         AND t.status NOT IN :finished
       """)
    long countOverdue(
            @Param("now") LocalDate now,
            @Param("finished") Collection<TaskStatus> finished
    );

    /**
     * "Overdue" = due date has passed and the task isn't in a state where
     * that no longer matters (finished or cancelled). REJECTED/BLOCKED stay
     * eligible since the employee still owes the work.
     */
    @Query("SELECT COUNT(t) FROM Task t WHERE t.dueDate < CURRENT_DATE AND t.status NOT IN :finishedStatuses")
    long countOverdue(@Param("finishedStatuses") List<TaskStatus> finishedStatuses);

    @EntityGraph(attributePaths = {"department", "assignee", "creator", "assignedBy"})
    List<Task> findTop20ByOrderByCreatedAtDesc();

    /**
     * Reminder scheduler candidates: not archived, not in a terminal status, has someone to remind.
     */
    @EntityGraph(attributePaths = {"assignee", "assignedBy", "creator"})
    @Query("SELECT t FROM Task t WHERE t.active = true AND t.status NOT IN :terminalStatuses AND t.assignee IS NOT NULL")
    List<Task> findActiveNonTerminalWithAssignee(@Param("terminalStatuses") List<TaskStatus> terminalStatuses);

    /** Manager KPI source: active top-level, non-cancelled tasks the given user assigned (matches the dashboard tiles). */
    @EntityGraph(attributePaths = {"assignee", "assignee.position"})
    // Top-level tasks plus group-task members (each member is one person's task). The group parent
    // is left out: it's a container, and counting it as well would count the work twice.
    @Query("SELECT t FROM Task t LEFT JOIN t.parentTask p WHERE t.active = true AND t.assignedBy.id = :assignedById "
            + "AND t.groupTask = false AND (p IS NULL OR p.groupTask = true) "
            + "AND t.status <> com.ratana.task_management_api.task.enums.TaskStatus.CANCELLED")
    List<Task> findActiveByAssignedById(@Param("assignedById") UUID assignedById);

    /**
     * Overdue alerts: active, non-group tasks due on {@code dueDate} that still owe work. Group parents
     * are left out — each member's subtask is alerted on its own.
     */
    @EntityGraph(attributePaths = {"assignee", "assignee.manager", "creator", "assignedBy", "assignments", "assignments.user"})
    @Query("SELECT DISTINCT t FROM Task t WHERE t.active = true AND t.groupTask = false AND t.assignee IS NOT NULL"
            + " AND t.dueDate = :dueDate AND t.status IN :statuses")
    List<Task> findActiveDueOnWithStatus(@Param("dueDate") LocalDate dueDate,
                                         @Param("statuses") Collection<TaskStatus> statuses);

    /**
     * Performance leaderboard source: active tasks where any of the users is the primary assignee or a
     * collaborator. Group parents are left out (each member subtask is that person's task).
     */
    @EntityGraph(attributePaths = {"assignee", "assignments", "assignments.user"})
    @Query("SELECT DISTINCT t FROM Task t LEFT JOIN t.assignee pa LEFT JOIN t.assignments a"
            + " WHERE t.active = true AND t.groupTask = false AND (pa.id IN :userIds OR a.user.id IN :userIds)")
    List<Task> findActiveForPerformance(@Param("userIds") Collection<UUID> userIds);

    /** User performance source: active tasks where the user is the primary assignee or a collaborator. */
    @Query("SELECT DISTINCT t FROM Task t LEFT JOIN t.assignee pa LEFT JOIN t.assignments a"
            + " WHERE t.active = true AND (pa.id = :userId OR a.user.id = :userId)")
    List<Task> findActiveByParticipant(@Param("userId") UUID userId);

    @Query("""
        SELECT
            COUNT(t.id) AS totalTasks,

            COALESCE(SUM(
                CASE
                    WHEN t.status IN (
                        'TODO',
                        'IN_PROGRESS',
                        'IN_REVIEW',
                        'REJECTED',
                        'BLOCKED'
                    )
                    THEN 1 ELSE 0
                END
            ), 0) AS activeTasks,

            COALESCE(SUM(
                CASE
                    WHEN t.status IN ('DONE', 'APPROVED')
                    THEN 1 ELSE 0
                END
            ), 0) AS completedTasks,

            COALESCE(SUM(
                CASE
                    WHEN t.status = 'IN_REVIEW'
                    THEN 1 ELSE 0
                END
            ), 0) AS pendingApproval,

            COALESCE(SUM(
                CASE
                    WHEN t.dueDate < :today
                    AND t.status NOT IN (
                        'DONE',
                        'APPROVED',
                        'CANCELLED'
                    )
                    THEN 1 ELSE 0
                END
            ), 0) AS overdueTasks

        FROM Task t

        WHERE (:managerId IS NULL
               OR t.creator.id = :managerId)

        AND (:departmentId IS NULL
             OR t.department.id = :departmentId)

        AND (:employeeId IS NULL
             OR t.assignee.id = :employeeId)
        """)
    DashboardTaskStatistics getTaskStatistics(
            @Param("managerId") UUID managerId,
            @Param("departmentId") UUID departmentId,
            @Param("employeeId") UUID employeeId,
            @Param("today") LocalDate today
    );


    @Query("""
        SELECT COUNT(DISTINCT t.assignee.id)
        FROM Task t
        WHERE t.creator.id = :managerId
        AND t.assignee IS NOT NULL
        """)
    long countDistinctTeamMembers(@Param("managerId") UUID managerId);


    @Query("""
        SELECT COUNT(t.id)
        FROM Task t
        WHERE t.assignedBy.id = :managerId
        """)
    long countAssignedByManager(
            @Param("managerId") UUID managerId
    );

    @Query("""
    SELECT
        COUNT(t.id) AS totalTasks,
        COALESCE(SUM(
            CASE WHEN t.status IN :activeStatuses
                 THEN 1 ELSE 0 END
        ), 0) AS activeTasks,
        COALESCE(SUM(
            CASE WHEN t.status IN :completedStatuses
                 THEN 1 ELSE 0 END
        ), 0) AS completedTasks,
        COALESCE(SUM(
            CASE WHEN t.status = :pendingStatus
                 THEN 1 ELSE 0 END
        ), 0) AS pendingApproval,
        COALESCE(SUM(
            CASE WHEN t.dueDate < :today
                  AND t.status NOT IN :excludedStatuses
                 THEN 1 ELSE 0 END
        ), 0) AS overdueTasks
    FROM Task t
    WHERE (:managerId IS NULL OR t.creator.id = :managerId)
      AND (:departmentId IS NULL OR t.department.id = :departmentId)
      AND (:employeeId IS NULL OR t.assignee.id = :employeeId)
    """)
    DashboardTaskStatistics getTaskStatistics(
            UUID managerId,
            UUID departmentId,
            UUID employeeId,
            LocalDate today,
            Collection<TaskStatus> activeStatuses,
            Collection<TaskStatus> completedStatuses,
            TaskStatus pendingStatus,
            Collection<TaskStatus> excludedStatuses
    );

    /**
     * Records the Telegram group message that announced the task. Native SQL on purpose: the
     * columns are read-only for JPA, and this must not bump the task's @Version (it runs after
     * the user's request, and would make their next edit fail with a stale-version conflict).
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE tasks SET telegram_chat_id = :chatId, telegram_message_id = :messageId WHERE id = :id",
            nativeQuery = true)
    void saveTelegramMessage(@Param("id") UUID id, @Param("chatId") String chatId,
                             @Param("messageId") Long messageId);
}
