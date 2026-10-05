package com.ratana.task_management_api.repository;

import com.ratana.task_management_api.dto.response.PageResponse;
import com.ratana.task_management_api.model.Attachment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    // @EntityGraph keeps this a single query (attachment + user + task), safe with Pageable
    // per the project's N+1 prevention strategy for *-to-one detail lookups.
//    @EntityGraph(attributePaths = {"user", "task"})
    Page<Attachment> findByTaskId(UUID taskId, Pageable pageable);

    Optional<Attachment> findById(UUID id);

    long countByTaskId(UUID taskId);

    /** Batched attachment counts for a page of tasks — avoids one COUNT query per task row. */
    @Query("SELECT a.task.id, COUNT(a) FROM Attachment a WHERE a.task.id IN :taskIds GROUP BY a.task.id")
    List<Object[]> countGroupedByTaskIdIn(@Param("taskIds") List<UUID> taskIds);
}
