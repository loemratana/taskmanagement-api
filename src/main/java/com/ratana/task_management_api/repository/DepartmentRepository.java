package com.ratana.task_management_api.repository;

import com.ratana.task_management_api.model.Department;
import org.apache.catalina.LifecycleState;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {


    @EntityGraph(attributePaths = {"manager"})
    List<Department> findAllByOrderByNameAsc();
    @EntityGraph(attributePaths = {"manager"})
    boolean existsByNameIgnoreCase(String name);
    @EntityGraph(attributePaths = {"manager"})
    Optional<Department> findById(UUID id);

}
