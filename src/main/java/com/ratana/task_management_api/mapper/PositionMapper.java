package com.ratana.task_management_api.mapper;

import com.ratana.task_management_api.dto.request.PositionRequest;
import com.ratana.task_management_api.dto.response.PositionResponse;
import com.ratana.task_management_api.model.Position;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PositionMapper {


    public PositionResponse toResponse(Position entity) {
        if (entity == null) {
            return null;
        }

        return PositionResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .departmentId(entity.getDepartment().getId())
                .departmentName(entity.getDepartment().getName())
                .createdDate(entity.getCreatedAt())
                .updatedDate(entity.getUpdatedAt())
                .build();
    }


    public List<PositionResponse> toResponseList(List<Position> entities) {
        return entities.stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * department is NOT set here — resolved via DepartmentRepository in the service.
     */
    public Position toEntity(PositionRequest request) {
        return Position.builder()
                .name(request.getTitle())
                .description(request.getDescription())
                .build();
    }

    public void updateEntityFromRequest(PositionRequest request, Position entity) {
        entity.setName(request.getTitle());
        entity.setDescription(request.getDescription());

    }
}
