package com.ratana.task_management_api.rbac.service;


import com.ratana.task_management_api.rbac.dto.request.PermissionRequest;
import com.ratana.task_management_api.rbac.dto.response.PermissionResponse;
import com.ratana.task_management_api.common.exception.DuplicateResourceException;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.rbac.mapper.PermissionMapper;
import com.ratana.task_management_api.rbac.entity.Permission;
import com.ratana.task_management_api.rbac.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;


    @Override
    @Transactional
    public PermissionResponse create(PermissionRequest request) {

        if (permissionRepository.existsByNameIgnoreCase(request.getName())) {
            log.warn("Permission creation rejected: {} already exists", request.getName());
            throw new DuplicateResourceException("Permission already exists"+request.getName());
        }

        Permission permission = permissionMapper.toEntity(request);
        permission.setActive(true);
        Permission saved = permissionRepository.save(permission);
        log.info("Permission created: {} ({})", saved.getName(), saved.getId());

        return permissionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PermissionResponse update(UUID id, PermissionRequest request) {

        Permission permission = permissionRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Permission not found"+id));

        if (request.getName() != null && !request.getName().equalsIgnoreCase(permission.getName())) {
            log.warn("Permission {} update rejected: name {} already exists", id, request.getName());
            throw new DuplicateResourceException("Permission already exists"+request.getName());
        }
        permissionMapper.updateEntityFromRequest(request,permission);
        Permission saved = permissionRepository.save(permission);
        log.info("Permission updated: {} ({})", saved.getName(), saved.getId());
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionResponse getById(UUID id) {
        log.debug("Fetching permission {}", id);
        return permissionMapper.toResponse(findEntityById(id));
    }

    @Override
    public List<PermissionResponse> getAll() {
        log.debug("Fetching all permissions");
        return permissionMapper.toResponse(permissionRepository.findAll());
    }

    @Override
    public void delete(UUID id) {

        Permission permission = findEntityById(id);
        // Soft delete — roles referencing this permission simply stop granting it going forward
        permission.setActive(false);
        permissionRepository.save(permission);
        log.info("Permission deactivated: {} ({})", permission.getName(), id);

    }

    private Permission findEntityById(UUID id) {
        return permissionRepository.findById(id).orElseThrow(() -> {
            log.warn("Permission not found: {}", id);
            return new ResourceNotFoundException("Permission not found"+id);
        });
    }
}
