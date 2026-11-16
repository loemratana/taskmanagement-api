package com.ratana.task_management_api.rbac.service;

import com.ratana.task_management_api.rbac.dto.request.PermissionRequest;
import com.ratana.task_management_api.rbac.dto.response.PermissionResponse;

import java.util.List;
import java.util.UUID;

public interface PermissionService {

    PermissionResponse create(PermissionRequest request);
    PermissionResponse update(UUID id, PermissionRequest request);
    PermissionResponse getById(UUID id);
    List<PermissionResponse> getAll();
    void delete(UUID id);
}
