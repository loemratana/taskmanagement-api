package com.ratana.task_management_api.repository;

import com.ratana.task_management_api.enums.UserStatus;
import com.ratana.task_management_api.model.User;
import com.ratana.task_management_api.repository.projection.OrgHierarchyRow;
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

    @EntityGraph("User.withRoleAndPermissions")
    Page<User> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {
            "department",
            "position",
            "manager",
            "role",
    })
    Optional<User> findById(UUID id);

    @EntityGraph(attributePaths = {"position"})
    List<User> findByDepartmentId(UUID departmentId);

    @Query(
            "SELECT u FROM User u LEFT JOIN FETCH u.role WHERE  u.id = :id"
    )
    Optional<User> findByIdWithRole(@Param("id") UUID id);


    @EntityGraph(attributePaths = {
            "role", "role.permissions"
    })
    Optional<User> findByEmail(String email);


    boolean existsByEmail(String email);



    @Query("SELECT u FROM User u WHERE u.manager IS NULL  AND u.status = :status")
    List<User> findTopLevelUsers(@Param("status") UserStatus status);

    @EntityGraph(attributePaths = {"department", "position", "manager", "role"})
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

    long countByRole_NameIgnoreCase(String roleName);

    // ------------------------------------------------------------------------
    // Recursive hierarchy (WITH RECURSIVE) — dynamic, manager_id-driven org structure.
    // Each query carries its own path[]-based cycle guard so a corrupt manager_id
    // chain can't run away instead of erroring.
    // ------------------------------------------------------------------------

    /**
     * The requested user's management chain: itself at level 0, then each successive
     * manager above it (level 1 = direct manager, level 2 = their manager, ...) up to the root.
     */
    @Query(value = """
            WITH RECURSIVE chain AS (
                SELECT u.id, u.full_name, u.email, u.avatar_url, u.position_id, u.department_id, u.manager_id,
                       0 AS level, ARRAY[u.id] AS path
                FROM users u
                WHERE u.id = :userId

                UNION ALL

                SELECT parent.id, parent.full_name, parent.email, parent.avatar_url, parent.position_id, parent.department_id, parent.manager_id,
                       c.level + 1, c.path || parent.id
                FROM users parent
                JOIN chain c ON parent.id = c.manager_id
                WHERE NOT parent.id = ANY(c.path)
            )
            SELECT c.id AS id, c.full_name AS fullName, c.email AS email, c.avatar_url AS avatarUrl,
                   c.position_id AS positionId, p.name AS positionName,
                   c.department_id AS departmentId, d.name AS departmentName,
                   c.manager_id AS managerId, c.level AS level
            FROM chain c
            LEFT JOIN positions p ON p.id = c.position_id
            LEFT JOIN departments d ON d.id = c.department_id
            ORDER BY c.level
            """, nativeQuery = true)
    List<OrgHierarchyRow> findManagementChain(@Param("userId") UUID userId);

    /**
     * Every employee under the given manager, at any depth (level 1 = direct reports,
     * level 2 = their reports, ...). The manager itself is not included.
     */
    @Query(value = """
            WITH RECURSIVE subtree AS (
                SELECT u.id, u.full_name, u.email, u.avatar_url, u.position_id, u.department_id, u.manager_id,
                       1 AS level, ARRAY[u.id] AS path
                FROM users u
                WHERE u.manager_id = :managerId

                UNION ALL

                SELECT child.id, child.full_name, child.email, child.avatar_url, child.position_id, child.department_id, child.manager_id,
                       s.level + 1, s.path || child.id
                FROM users child
                JOIN subtree s ON child.manager_id = s.id
                WHERE NOT child.id = ANY(s.path)
            )
            SELECT s.id AS id, s.full_name AS fullName, s.email AS email, s.avatar_url AS avatarUrl,
                   s.position_id AS positionId, p.name AS positionName,
                   s.department_id AS departmentId, d.name AS departmentName,
                   s.manager_id AS managerId, s.level AS level
            FROM subtree s
            LEFT JOIN positions p ON p.id = s.position_id
            LEFT JOIN departments d ON d.id = s.department_id
            ORDER BY s.path
            """, nativeQuery = true)
    List<OrgHierarchyRow> findSubtreeUnderManager(@Param("managerId") UUID managerId);

    /**
     * The full organization tree: every top-level employee (manager_id IS NULL) as a
     * root, plus their full downline. Flat, path-ordered rows — the caller assembles
     * the nested tree so this stays a single query with no N+1.
     */
    @Query(value = """
            WITH RECURSIVE org_tree AS (
                SELECT u.id, u.full_name, u.email, u.avatar_url, u.position_id, u.department_id, u.manager_id,
                       0 AS level, ARRAY[u.id] AS path
                FROM users u
                WHERE u.manager_id IS NULL

                UNION ALL

                SELECT child.id, child.full_name, child.email, child.avatar_url, child.position_id, child.department_id, child.manager_id,
                       t.level + 1, t.path || child.id
                FROM users child
                JOIN org_tree t ON child.manager_id = t.id
                WHERE NOT child.id = ANY(t.path)
            )
            SELECT t.id AS id, t.full_name AS fullName, t.email AS email, t.avatar_url AS avatarUrl,
                   t.position_id AS positionId, p.name AS positionName,
                   t.department_id AS departmentId, d.name AS departmentName,
                   t.manager_id AS managerId, t.level AS level
            FROM org_tree t
            LEFT JOIN positions p ON p.id = t.position_id
            LEFT JOIN departments d ON d.id = t.department_id
            ORDER BY t.path
            """, nativeQuery = true)
    List<OrgHierarchyRow> findOrganizationTree();

    /**
     * True if {@code prospectiveManagerId} is {@code userId} itself or already sits
     * above {@code userId} in the chain — i.e. assigning it as {@code userId}'s manager
     * would create a cycle. Single round trip, cycle-guarded via path[].
     */
    @Query(value = """
            WITH RECURSIVE chain AS (
                SELECT u.id, u.manager_id, ARRAY[u.id] AS path
                FROM users u
                WHERE u.id = :prospectiveManagerId

                UNION ALL

                SELECT parent.id, parent.manager_id, c.path || parent.id
                FROM users parent
                JOIN chain c ON parent.id = c.manager_id
                WHERE NOT parent.id = ANY(c.path)
            )
            SELECT EXISTS (SELECT 1 FROM chain WHERE id = :userId)
            """, nativeQuery = true)
    boolean wouldCreateManagerCycle(@Param("userId") UUID userId, @Param("prospectiveManagerId") UUID prospectiveManagerId);


    @EntityGraph(attributePaths = {"department", "position"})
    List<User> findByDepartmentIdAndActiveTrue(UUID departmentId);


    @EntityGraph(attributePaths = {"department","position"})
    List<User> findByDepartmentIdAndPositionIdAndActiveTrue(UUID departmentId, UUID positionId);
}
