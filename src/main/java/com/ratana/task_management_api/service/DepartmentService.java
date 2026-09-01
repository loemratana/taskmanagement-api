package com.ratana.task_management_api.service;

import com.ratana.task_management_api.dto.request.DepartmentRequest;
import com.ratana.task_management_api.dto.response.DepartmentMemberResponse;
import com.ratana.task_management_api.dto.response.DepartmentResponse;
import com.ratana.task_management_api.dto.response.DepartmentTreeResponse;
import org.apache.coyote.BadRequestException;

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
