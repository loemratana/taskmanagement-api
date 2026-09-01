package com.ratana.task_management_api.service;

import com.ratana.task_management_api.dto.request.DepartmentTelegramGroupRequest;
import com.ratana.task_management_api.dto.response.DepartmentTelegramGroupResponse;
import com.ratana.task_management_api.model.DepartmentTelegramGroup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentTelegramGroupService {

    DepartmentTelegramGroupResponse create(DepartmentTelegramGroupRequest request);

    DepartmentTelegramGroupResponse update(UUID id, DepartmentTelegramGroupRequest request);

    DepartmentTelegramGroupResponse getByDepartment(UUID departmentId);

    List<DepartmentTelegramGroupResponse> getAll();

    void deactivate(UUID id);

    void sendTestMessage(UUID id);

    /** Used by TelegramGroupNotificationSender — not exposed over the API. */
    Optional<DepartmentTelegramGroup> resolveActive(UUID departmentId);
}
