package com.ratana.task_management_api.rbac.mapper;

import com.ratana.task_management_api.rbac.dto.request.RoleRequest;
import com.ratana.task_management_api.rbac.dto.response.PermissionSummary;
import com.ratana.task_management_api.rbac.dto.response.RoleResponse;
import com.ratana.task_management_api.rbac.entity.Permission;
import com.ratana.task_management_api.rbac.entity.Role;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class RoleMapper {

    public RoleResponse toResponse(Role entity) {
        if (entity == null) return null;

        List<PermissionSummary> permissions = entity.getPermissions()
                .stream()
                .filter(p -> Boolean.TRUE.equals(p.isActive()))
                .map(this::toPermissionSummary)
                .toList();

        return RoleResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .active(entity.isActive())
                .permissions(permissions)
                .created(entity.getCreatedAt())
                .updated(entity.getUpdatedAt())
                .build();
    }

    public List<RoleResponse> toResponseList(List<Role> entities) {
        return entities.stream().map(this::toResponse).toList();
    }

    public Role toEntity(RoleRequest request) {
        Role entity = new Role();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        return entity;
    }

    public void updateEntityFromRequest(RoleRequest request, Role entity) {
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
    }

    public PermissionSummary toPermissionSummary(Permission entity) {

        return PermissionSummary.builder()
                .id(entity.getId())
                .name(entity.getName())
                .model(entity.getModule())
                .action(entity.getAction())
                .build();
    }

}
