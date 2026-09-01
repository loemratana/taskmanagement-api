package com.ratana.task_management_api.service;

import com.ratana.task_management_api.dto.request.*;
import com.ratana.task_management_api.dto.response.PageResponse;
import com.ratana.task_management_api.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface UserService {

    UserResponse create(UserCreateRequest request);

    UserResponse getById(UUID id);

    PageResponse<UserResponse> search(UserFilterRequest filter, Pageable pageable);

    UserResponse update(UUID id, UserUpdateRequest request);

    UserResponse uploadAvatar(UUID id , MultipartFile file);
    void removeAvatar(UUID id);

    void deactivate(UUID id);

    void activate(UUID id);

    UserResponse assignRoles(UUID id, AssignRolesRequest request);

    UserResponse assignManager(UUID id, AssignManagerRequest request);

    UserResponse assignOrg(UUID id, AssignOrgRequest request);

    void changePassword(UUID id, ChangePasswordRequest request);

    List<UserResponse> getDirectReports(UUID managerId);
}
