package com.ratana.task_management_api.rbac.service;

import com.ratana.task_management_api.user.dto.request.UserCreateRequest;
import com.ratana.task_management_api.user.dto.response.UserResponse;

import java.util.UUID;

public interface UserRoleService {


    UserResponse create(UserCreateRequest request);
    UserResponse update(UUID id, UserCreateRequest request);

    UserResponse removeRole(UUID id, UUID roleId);

    void activate(UUID id);
    void deactivate(UUID id);
}
