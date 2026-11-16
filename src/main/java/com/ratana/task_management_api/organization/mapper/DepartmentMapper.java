package com.ratana.task_management_api.organization.mapper;


import com.ratana.task_management_api.organization.dto.request.DepartmentRequest;
import com.ratana.task_management_api.organization.dto.response.DepartmentResponse;
import com.ratana.task_management_api.user.dto.response.ManagerResponse;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class DepartmentMapper {

    /**
     * Builds a new entity from a create request.
     * parent/active are intentionally NOT set here — parent requires a DB lookup
     * (done in the service), and active always starts true on creation.
     */

    public Department toEntity(DepartmentRequest request) {

       return Department.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
    }


    /**
     * Applies request fields onto an existing entity for update.
     * parent is NOT touched here — resolved and set separately in the service.
     */
    public void updateEntityFromRequest(DepartmentRequest request,Department  department) {

          department.setName(request.getName());
          department.setDescription(request.getDescription());

    }



    public DepartmentResponse toResponse(Department entity) {
        if (entity == null) {
            return null;
        }
        return DepartmentResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .manager(toManagerResponse(entity.getManager()))
                .active(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public List<DepartmentResponse> toResponseList(List<Department> entities)
    {
        return entities.stream().map(this::toResponse).toList();

    }

    public ManagerResponse toManagerResponse(User manager) {

        if (manager == null) {
            return null;
        }

        return new ManagerResponse(
                manager.getId(),
                manager.getFullName(),
                manager.getEmail(),
                manager.getAvatarUrl()
        );
    }

    private UUID getManagerId(Department department) {

        return department.getManager() != null
                ? department.getManager().getId()
                : null;
    }
}
