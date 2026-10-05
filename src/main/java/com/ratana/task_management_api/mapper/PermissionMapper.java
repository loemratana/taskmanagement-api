package com.ratana.task_management_api.mapper;


import com.ratana.task_management_api.dto.request.PermissionRequest;
import com.ratana.task_management_api.dto.response.PermissionResponse;
import com.ratana.task_management_api.model.Permission;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class PermissionMapper {

    public PermissionResponse toResponse(Permission entity) {
        if (entity == null) return null;


        return PermissionResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .module(entity.getModule())
                .action(entity.getAction())
                .description(entity.getDescription())
                .active(Boolean.TRUE.equals(entity.isActive()))
                .systemDefined(Boolean.TRUE.equals(entity.isSystemDefined()))
                .build();

    }

    public List<PermissionResponse> toResponse(List<Permission> entityList) {
        return entityList.stream().map(this::toResponse).toList();
    }

    public Permission toEntity(PermissionRequest request)
    {
        return Permission.builder()
                .name(request.getName())
                .action(request.getAction())
                .module(request.getModule())
                .description(request.getDescription())
                .build();
    }

    public void updateEntityFromRequest(PermissionRequest request, Permission entity) {
        if (StringUtils.hasText(request.getName())) {
            entity.setName(request.getName());
        }

        if (StringUtils.hasText(request.getModule())) {
            entity.setModule(request.getModule());
        }

        if (StringUtils.hasText(request.getAction())) {
            entity.setAction(request.getAction());
        }

        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
    }
}
