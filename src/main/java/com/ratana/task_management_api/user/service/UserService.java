package com.ratana.task_management_api.user.service;

import com.ratana.task_management_api.auth.dto.request.ChangePasswordRequest;
import com.ratana.task_management_api.rbac.dto.request.AssignRolesRequest;
import com.ratana.task_management_api.user.dto.request.AssignManagerRequest;
import com.ratana.task_management_api.user.dto.request.AssignOrgRequest;
import com.ratana.task_management_api.user.dto.request.UserCreateRequest;
import com.ratana.task_management_api.user.dto.request.UserFilterRequest;
import com.ratana.task_management_api.user.dto.request.UserUpdateRequest;


import com.ratana.task_management_api.organization.dto.response.EmployeeHierarchyResponse;
import com.ratana.task_management_api.user.dto.response.ManagerResponse;
import com.ratana.task_management_api.organization.dto.response.OrgTreeNodeResponse;
import com.ratana.task_management_api.common.pagination.PageResponse;
import com.ratana.task_management_api.user.dto.response.UserResponse;
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

    ManagerResponse getManager(UUID id);

    EmployeeHierarchyResponse getHierarchy(UUID id);

    List<OrgTreeNodeResponse> getOrganizationTree();
}
