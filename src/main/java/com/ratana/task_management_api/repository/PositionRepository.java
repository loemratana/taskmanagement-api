package com.ratana.task_management_api.repository;

import com.ratana.task_management_api.model.Position;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;


@Repository
public interface PositionRepository extends JpaRepository<Position, UUID> {

    @Query("SELECT p FROM Position p JOIN FETCH p.department WHERE p.active = true")
    List<Position> findAllActiveWithDepartment();

    boolean findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    @Query("""
        SELECT p 
        FROM Position p
        JOIN FETCH p.department
        """)
    List<Position> findAllWithDepartment();

}
