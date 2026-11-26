package com.ratana.task_management_api.user.repository;

import com.ratana.task_management_api.user.enums.UserStatus;
import com.ratana.task_management_api.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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

    @EntityGraph(attributePaths = {"department", "position"})
    List<User> findByPositionId(UUID positionId);

    @Query(
            "SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE  u.id = :id"
    )
    Optional<User> findByIdWithRoles(@Param("id") UUID id);


    // Runs on every authenticated request (JwtAuthenticationFilter): fetch roles and their
    // permissions in one query instead of one extra query per role.
    @EntityGraph("User.withRolesAndPermissions")
    Optional<User> findByEmail(String email);


    boolean existsByEmail(String email);



    @Query("SELECT u FROM User u WHERE u.manager IS NULL  AND u.status = :status")
    List<User> findTopLevelUsers(@Param("status") UserStatus status);

    @EntityGraph(attributePaths = {
            "department",
            "position",
            "manager",
            "roles",
    })
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

    /**
     * Users per role, for several roles in one query: rows of [UPPER(role name), user count].
     * Same rule as {@link #countByRoles_NameIgnoreCase}; pass the names in upper case.
     */
    @Query("SELECT UPPER(r.name), COUNT(DISTINCT u.id) FROM User u JOIN u.roles r"
            + " WHERE UPPER(r.name) IN :roleNames GROUP BY UPPER(r.name)")
    List<Object[]> countUsersByRoleName(@Param("roleNames") Collection<String> roleNames);

    /**
     * Loads every user with department and position in one query, for building the
     * reporting hierarchy in memory. manager is left lazy — only its id is read.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.department LEFT JOIN FETCH u.position")
    List<User> findAllForOrgChart();

    // ---- Announcement recipients (active users only) ----

    @Query("SELECT u.id FROM User u WHERE u.active = true")
    List<UUID> findActiveUserIds();

    @Query("SELECT u.id FROM User u WHERE u.active = true AND u.department.id IN :departmentIds")
    List<UUID> findActiveUserIdsByDepartmentIds(@Param("departmentIds") Collection<UUID> departmentIds);

    /** TEAM audience = direct reports of the selected managers. */
    @Query("SELECT u.id FROM User u WHERE u.active = true AND u.manager.id IN :managerIds")
    List<UUID> findActiveUserIdsByManagerIds(@Param("managerIds") Collection<UUID> managerIds);

    @Query("SELECT u.id FROM User u WHERE u.active = true AND u.id IN :ids")
    List<UUID> findActiveUserIdsByIdIn(@Param("ids") Collection<UUID> ids);

    // ---- Meeting booking audience (enabled accounts only, see User#isAccountEnabled) ----

    @Query("""
            SELECT u.id FROM User u
            WHERE u.active = true AND u.status = com.ratana.task_management_api.user.enums.UserStatus.ACTIVE
              AND u.department.id IN :departmentIds
            """)
    List<UUID> findEnabledUserIdsByDepartmentIds(@Param("departmentIds") Collection<UUID> departmentIds);

    @Query("""
            SELECT u.id FROM User u
            WHERE u.active = true AND u.status = com.ratana.task_management_api.user.enums.UserStatus.ACTIVE
              AND u.position.id IN :positionIds
            """)
    List<UUID> findEnabledUserIdsByPositionIds(@Param("positionIds") Collection<UUID> positionIds);

    /** Selected users with their department, for the booking scope check. Disabled accounts included. */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.department WHERE u.id IN :ids")
    List<User> findAllWithDepartmentByIdIn(@Param("ids") Collection<UUID> ids);
}
