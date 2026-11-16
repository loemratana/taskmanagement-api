package com.ratana.task_management_api.booking.repository;

import com.ratana.task_management_api.booking.entity.UserBookingScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface UserBookingScopeRepository extends JpaRepository<UserBookingScope, UUID> {

    @Query("SELECT s.department.id FROM UserBookingScope s WHERE s.user.id = :userId")
    Set<UUID> findDepartmentIdsByUserId(@Param("userId") UUID userId);

    @Query("SELECT s FROM UserBookingScope s JOIN FETCH s.department WHERE s.user.id = :userId ORDER BY s.department.name")
    List<UserBookingScope> findByUserIdWithDepartment(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM UserBookingScope s WHERE s.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
