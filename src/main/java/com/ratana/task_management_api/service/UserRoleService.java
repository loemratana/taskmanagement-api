package com.ratana.task_management_api.service;

import com.ratana.task_management_api.dto.request.UserCreateRequest;
import com.ratana.task_management_api.dto.response.UserResponse;

import java.util.UUID;

public interface UserRoleService {


    UserResponse create(UserCreateRequest request);
    UserResponse update(UUID id, UserCreateRequest request);

    UserResponse removeRole(UUID id, UUID roleId);

    void activate(UUID id);
    void deactivate(UUID id);
}
