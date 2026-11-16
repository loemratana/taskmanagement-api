package com.ratana.task_management_api.organization.service;

import com.ratana.task_management_api.organization.dto.request.DepartmentRequest;
import com.ratana.task_management_api.organization.dto.response.DepartmentMemberResponse;
import com.ratana.task_management_api.organization.dto.response.DepartmentResponse;
import com.ratana.task_management_api.organization.dto.response.DepartmentTreeResponse;

import java.util.List;
import java.util.UUID;

public interface DepartmentService {

    DepartmentResponse create(DepartmentRequest request);

    DepartmentResponse update(UUID id, DepartmentRequest request);

    DepartmentResponse getById(UUID id);

    List<DepartmentTreeResponse> getTree();

    List<DepartmentResponse> getAllFlat();

    DepartmentResponse assignManager(UUID departmentId, UUID managerId);

    void delete(UUID id);

    List<DepartmentMemberResponse> getMembers(UUID departmentId);
}
