package com.ratana.task_management_api.service.ServiceImpl;


import com.ratana.task_management_api.dto.request.PermissionRequest;
import com.ratana.task_management_api.dto.response.PermissionResponse;
import com.ratana.task_management_api.exception.DuplicateResourceException;
import com.ratana.task_management_api.exception.ResourceNotFoundException;
import com.ratana.task_management_api.mapper.PermissionMapper;
import com.ratana.task_management_api.model.Permission;
import com.ratana.task_management_api.repository.PermissionRepository;
import com.ratana.task_management_api.service.PermissionService;
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
        return permissionMapper.toResponse(findEntityById(id));
    }

    @Override
    public List<PermissionResponse> getAll() {
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
        return permissionRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Permission not found"+id));
    }
}
