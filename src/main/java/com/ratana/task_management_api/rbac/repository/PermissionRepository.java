package com.ratana.task_management_api.rbac.repository;

import com.ratana.task_management_api.rbac.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Permission findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    List<Permission> findAllByActiveTrue();
    List<Permission> findAllByActiveFalse();

    Set<Permission> findAllByIdInAndActiveTrue(Set<UUID> ids);


}
