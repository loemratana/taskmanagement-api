package com.ratana.task_management_api.repository;

import com.ratana.task_management_api.model.DepartmentTelegramGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartmentTelegramGroupRepository extends JpaRepository<DepartmentTelegramGroup, UUID> {

    Optional<DepartmentTelegramGroup> findByDepartmentId(UUID departmentId);

    Optional<DepartmentTelegramGroup> findByDepartmentIdAndActiveTrue(UUID departmentId);

    boolean existsByDepartmentId(UUID departmentId);
}
