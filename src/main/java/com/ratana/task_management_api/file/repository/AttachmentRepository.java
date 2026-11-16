package com.ratana.task_management_api.file.repository;

import com.ratana.task_management_api.file.entity.Attachment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;


@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    // @EntityGraph keeps this a single query (attachment + user + task), safe with Pageable
    // per the project's N+1 prevention strategy for *-to-one detail lookups.
    @EntityGraph(attributePaths = {"user", "task"})
    Page<Attachment> findByTaskId(UUID taskId, Pageable pageable);

    Optional<Attachment> findById(UUID id);

    long countByTaskId(UUID taskId);
}
