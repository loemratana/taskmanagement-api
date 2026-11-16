package com.ratana.task_management_api.rbac.service;

import com.ratana.task_management_api.rbac.dto.request.AssignPermissionsRequest;
import com.ratana.task_management_api.rbac.dto.request.RoleRequest;
import com.ratana.task_management_api.rbac.dto.response.RoleResponse;
import com.ratana.task_management_api.common.exception.DuplicateResourceException;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.rbac.mapper.RoleMapper;
import com.ratana.task_management_api.rbac.entity.Permission;
import com.ratana.task_management_api.rbac.entity.Role;
import com.ratana.task_management_api.rbac.repository.PermissionRepository;
import com.ratana.task_management_api.rbac.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleMapper roleMapper;

    @Override
    @Transactional
    public RoleResponse create(RoleRequest request) {
        if (roleRepository.existsByNameIgnoreCase(request.getName())) {
            log.warn("Role creation rejected: name {} already exists", request.getName());
            throw new DuplicateResourceException("Role already exists: " + request.getName());
        }

        Role role = roleMapper.toEntity(request);
        role.setActive(true);
        Set<Permission> permissions = new HashSet<>(
                permissionRepository.findAllById(request.getPermissionIds())
        );

        role.setPermissions(permissions);

        Role saved = roleRepository.save(role);
        log.info("Role created: {} ({})", saved.getName(), saved.getId());

        return roleMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public RoleResponse update(UUID id, RoleRequest request) {
        Role role = findEntityById(id);

        if (!request.getName().equalsIgnoreCase(role.getName())
                && roleRepository.existsByNameIgnoreCase(request.getName())) {
            log.warn("Role {} update rejected: name {} already exists", id, request.getName());
            throw new DuplicateResourceException("Role already exists: " + request.getName());
        }

        roleMapper.updateEntityFromRequest(request, role);

        Role saved = roleRepository.save(role);
        log.info("Role updated: {} ({})", saved.getName(), saved.getId());

        return roleMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getById(UUID id) {
        log.debug("Fetching role {}", id);
        Role role = roleRepository.findWithPermissionsById(id)
                .orElseThrow(() -> {
                    log.warn("Role not found: {}", id);
                    return new ResourceNotFoundException("Role not found with id: " + id);
                });
        return roleMapper.toResponse(role);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getAll() {
        log.debug("Fetching all active roles");
        return roleMapper.toResponseList(roleRepository.findAllByActiveTrue());
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Role role = findEntityById(id);
        // Soft delete — users holding this role simply lose its permissions going forward
        role.setActive(false);
        roleRepository.save(role);
        log.info("Role deactivated: {} ({})", role.getName(), id);
    }

    @Override
    @Transactional
    public RoleResponse assignPermissions(UUID roleId, AssignPermissionsRequest request) {
        Role role = findEntityById(roleId);
        Set<Permission> permission = permissionRepository.findAllByIdInAndActiveTrue(request.getPermissionIds());

        if (permission.size() != request.getPermissionIds().size()) {
            log.warn("Permission assignment for role {} rejected: invalid or inactive id in {}", roleId, request.getPermissionIds());
            throw new ResourceNotFoundException("One or more permission IDs are invalid or inactive");

        }
        role.setPermissions(permission);
        Role saved = roleRepository.save(role);
        log.info("Permissions updated for role {}: {} permission(s) assigned", role.getName(), permission.size());
        return roleMapper.toResponse(saved);
    }

    private Role findEntityById(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Role not found: {}", id);
                    return new ResourceNotFoundException("Role not found with id: " + id);
                });
    }
}
