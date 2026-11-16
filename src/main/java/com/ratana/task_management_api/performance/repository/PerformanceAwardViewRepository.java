package com.ratana.task_management_api.performance.repository;

import com.ratana.task_management_api.performance.entity.PerformanceAwardView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PerformanceAwardViewRepository extends JpaRepository<PerformanceAwardView, UUID> {

    boolean existsByAwardIdAndUserId(UUID awardId, UUID userId);
}
