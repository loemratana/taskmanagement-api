package com.ratana.task_management_api.rbac.repository;

import com.ratana.task_management_api.rbac.entity.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    @EntityGraph(attributePaths = "permissions")
    List<Role> findAllByActiveTrue();

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findWithPermissionsById(UUID id);

    Set<Role> findAllByIdInAndActiveTrue(Set<UUID> ids);

}
