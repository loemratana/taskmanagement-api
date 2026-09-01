package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.dto.request.AssignPermissionsRequest;
import com.ratana.task_management_api.dto.request.RoleRequest;
import com.ratana.task_management_api.dto.response.RoleResponse;
import com.ratana.task_management_api.exception.DuplicateResourceException;
import com.ratana.task_management_api.exception.ResourceNotFoundException;
import com.ratana.task_management_api.mapper.RoleMapper;
import com.ratana.task_management_api.model.Permission;
import com.ratana.task_management_api.model.Role;
import com.ratana.task_management_api.repository.PermissionRepository;
import com.ratana.task_management_api.repository.RoleRepository;
import com.ratana.task_management_api.service.RoleService;
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
        Role role = roleRepository.findWithPermissionsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + id));
        return roleMapper.toResponse(role);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getAll() {
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
            throw new ResourceNotFoundException("One or more permission IDs are invalid or inactive");

        }
        role.setPermissions(permission);
        Role saved = roleRepository.save(role);
        log.info("Permissions updated for role {}: {} permission(s) assigned", role.getName(), permission);
        return roleMapper.toResponse(saved);
    }

    private Role findEntityById(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + id));
    }
}
