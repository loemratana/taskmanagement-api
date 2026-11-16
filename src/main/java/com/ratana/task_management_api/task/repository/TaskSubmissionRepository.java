package com.ratana.task_management_api.task.repository;

import com.ratana.task_management_api.task.enums.SubmissionStatus;
import com.ratana.task_management_api.task.entity.TaskSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission, UUID> {

    @EntityGraph(attributePaths = {"files", "submittedBy", "reviewedBy"})
    List<TaskSubmission> findByTaskIdOrderByRoundDesc(UUID taskId);

    @EntityGraph(attributePaths = {"files", "submittedBy", "reviewedBy", "task"})
    Optional<TaskSubmission> findByIdAndTaskId(UUID id, UUID taskId);

    /** Only to-one paths here: fetching the files collection with paging would page in memory. */
    @EntityGraph(attributePaths = {"task", "submittedBy", "reviewedBy"})
    Page<TaskSubmission> findBySubmittedById(UUID submittedById, Pageable pageable);

    @EntityGraph(attributePaths = {"task", "submittedBy", "reviewedBy"})
    Page<TaskSubmission> findBySubmittedByIdAndStatus(UUID submittedById, SubmissionStatus status, Pageable pageable);

    /** Review queue: excludes the reviewer's own submissions and archived tasks. */
    @EntityGraph(attributePaths = {"task", "submittedBy", "reviewedBy"})
    Page<TaskSubmission> findByStatusAndTaskActiveTrueAndSubmittedByIdNot(
            SubmissionStatus status, UUID reviewerId, Pageable pageable);

    @EntityGraph(attributePaths = {"task", "submittedBy", "reviewedBy"})
    Page<TaskSubmission> findByStatusAndTaskDepartmentIdAndTaskActiveTrueAndSubmittedByIdNot(
            SubmissionStatus status, UUID departmentId, UUID reviewerId, Pageable pageable);

    /** Submissions on active tasks whose primary assignee reports directly to the given manager. */
    @EntityGraph(attributePaths = {"task", "submittedBy", "reviewedBy"})
    @Query(value = "SELECT s FROM TaskSubmission s WHERE s.status = :status AND s.task.active = true"
            + " AND s.task.assignee.manager.id = :managerId AND s.submittedBy.id <> :managerId",
            countQuery = "SELECT COUNT(s) FROM TaskSubmission s WHERE s.status = :status AND s.task.active = true"
                    + " AND s.task.assignee.manager.id = :managerId AND s.submittedBy.id <> :managerId")
    Page<TaskSubmission> findByStatusForDirectReportsOf(
            @Param("status") SubmissionStatus status, @Param("managerId") UUID managerId, Pageable pageable);

    /** User performance source: every submission the user made, with its task for date filtering. */
    @EntityGraph(attributePaths = {"task"})
    List<TaskSubmission> findAllBySubmittedById(UUID submittedById);

    /** Performance leaderboard source: every submission on the given tasks, whoever made it. */
    @Query("SELECT s FROM TaskSubmission s WHERE s.task.id IN :taskIds")
    List<TaskSubmission> findByTaskIds(@Param("taskIds") Collection<UUID> taskIds);

    boolean existsByTaskIdAndStatus(UUID taskId, SubmissionStatus status);

    @Query("select coalesce(max(s.round), 0) from TaskSubmission s where s.task.id = :taskId")
    int findMaxRound(@Param("taskId") UUID taskId);

    /** Records the Telegram group message that posted the submission (columns are read-only for JPA). */
    @Modifying
    @Transactional
    @Query(value = "UPDATE task_submissions SET telegram_chat_id = :chatId, telegram_message_id = :messageId "
            + "WHERE id = :id", nativeQuery = true)
    void saveTelegramMessage(@Param("id") UUID id, @Param("chatId") String chatId,
                             @Param("messageId") Long messageId);
}
