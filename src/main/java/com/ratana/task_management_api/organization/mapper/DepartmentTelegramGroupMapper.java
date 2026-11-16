package com.ratana.task_management_api.organization.mapper;


import com.ratana.task_management_api.organization.dto.request.DepartmentTelegramGroupRequest;
import com.ratana.task_management_api.organization.dto.response.DepartmentTelegramGroupResponse;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.organization.entity.DepartmentTelegramGroup;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DepartmentTelegramGroupMapper {

    /**
     * Builds a new entity from a create request. department is passed in rather than
     * resolved here — the lookup (and the "does it exist" check) belongs in the service.
     */
    public DepartmentTelegramGroup toEntity(DepartmentTelegramGroupRequest request, Department department) {
        return DepartmentTelegramGroup.builder()
                .department(department)
                .chatId(request.getChatId())
                .groupName(request.getGroupName())
                .active(true)
                .build();
    }

    /**
     * Applies request fields onto an existing entity for update. department is intentionally
     * NOT touched here — a mapping's department is immutable after creation (see service).
     */
    public void updateEntityFromRequest(DepartmentTelegramGroupRequest request, DepartmentTelegramGroup group) {
        group.setChatId(request.getChatId());
        group.setGroupName(request.getGroupName());
    }

    public DepartmentTelegramGroupResponse toResponse(DepartmentTelegramGroup entity) {
        if (entity == null) {
            return null;
        }
        return DepartmentTelegramGroupResponse.builder()
                .id(entity.getId())
                .departmentId(entity.getDepartment().getId())
                .departmentName(entity.getDepartment().getName())
                .chatId(entity.getChatId())
                .groupName(entity.getGroupName())
                .active(entity.isActive())
                .verifiedAt(entity.getVerifiedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public List<DepartmentTelegramGroupResponse> toResponseList(List<DepartmentTelegramGroup> entities) {
        return entities.stream().map(this::toResponse).toList();
    }
}
