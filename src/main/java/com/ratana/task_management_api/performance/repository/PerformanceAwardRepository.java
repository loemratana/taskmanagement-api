package com.ratana.task_management_api.performance.repository;

import com.ratana.task_management_api.performance.entity.PerformanceAward;
import com.ratana.task_management_api.performance.enums.PerformancePeriod;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PerformanceAwardRepository extends JpaRepository<PerformanceAward, UUID> {

    boolean existsByDepartmentIdAndPeriodAndPeriodYearAndPeriodNumber(
            UUID departmentId, PerformancePeriod period, Integer periodYear, Integer periodNumber);

    @EntityGraph(attributePaths = "entries")
    @Query("SELECT a FROM PerformanceAward a WHERE a.id = :id")
    Optional<PerformanceAward> findWithEntriesById(@Param("id") UUID id);

    /** The department's awards published since {@code since} that the user hasn't closed yet, newest first. */
    @Query("SELECT a FROM PerformanceAward a WHERE a.departmentId = :departmentId AND a.publishedAt >= :since"
            + " AND NOT EXISTS (SELECT v.id FROM PerformanceAwardView v WHERE v.awardId = a.id AND v.userId = :userId)"
            + " ORDER BY a.publishedAt DESC")
    List<PerformanceAward> findPending(@Param("departmentId") UUID departmentId, @Param("userId") UUID userId,
                                       @Param("since") Instant since, Pageable pageable);
}
