package com.ratana.task_management_api.repository;

import com.ratana.task_management_api.enums.UserStatus;
import com.ratana.task_management_api.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface UserRepository extends JpaRepository<User, UUID> , JpaSpecificationExecutor<User> {

    @EntityGraph("User.withRolesAndPermissions")
    Page<User> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {
            "department",
            "position",
            "manager",
            "roles",
    })
    Optional<User> findById(UUID id);

    @EntityGraph(attributePaths = {"position"})
    List<User> findByDepartmentId(UUID departmentId);

    @Query(
            "SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE  u.id = :id"
    )
    Optional<User> findByIdWithRoles(@Param("id") UUID id);


    @EntityGraph(attributePaths = {
            "roles"
    })
    Optional<User> findByEmail(String email);


    boolean existsByEmail(String email);



    @Query("SELECT u FROM User u WHERE u.manager IS NULL  AND u.status = :status")
    List<User> findTopLevelUsers(@Param("status") UserStatus status);

    List<User> findUserByManagerId(UUID managerId);


    boolean findByActiveIsFalse(boolean active);

    /**
     * Retrieves only the department ID of a user, avoiding the full entity load.
     */
    @Query("SELECT u.department.id FROM User u WHERE u.id = :userId")
    Optional<UUID> findDepartmentIdByUserId(@Param("userId") UUID userId);

    /**
     * Checks if a given user belongs to a specific department.
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END " +
            "FROM User u WHERE u.id = :userId AND u.department.id = :departmentId")
    boolean isUserInDepartment(@Param("userId") UUID userId,
                               @Param("departmentId") UUID departmentId);

    // ------------------------------------------------------------------------
    // Direct reports (for supervisors/managers)
    // ------------------------------------------------------------------------

    /**
     * Returns a list of user IDs who report directly to the given manager.
     * This is used for the TASK_ASSIGN_TEAM permission check.
     */
    @Query("SELECT u.id FROM User u WHERE u.manager.id = :managerId")
    List<UUID> findDirectReportIds(@Param("managerId") UUID managerId);

    long countByRoles_NameIgnoreCase(String roleName);
}
