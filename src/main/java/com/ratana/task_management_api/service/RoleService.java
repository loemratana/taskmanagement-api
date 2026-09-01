package com.ratana.task_management_api.service;

import com.ratana.task_management_api.dto.request.AssignPermissionsRequest;
import com.ratana.task_management_api.dto.request.RoleRequest;
import com.ratana.task_management_api.dto.response.RoleResponse;

import java.util.List;
import java.util.UUID;

public interface RoleService {
    RoleResponse create(RoleRequest request);
    RoleResponse update(UUID id, RoleRequest request);
    RoleResponse getById(UUID id);
    List<RoleResponse> getAll();
    void delete(UUID id);
    RoleResponse assignPermissions(UUID roleId, AssignPermissionsRequest request);
}
