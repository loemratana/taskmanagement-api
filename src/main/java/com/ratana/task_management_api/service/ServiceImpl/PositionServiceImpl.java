package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.dto.request.PositionRequest;
import com.ratana.task_management_api.dto.response.PositionResponse;
import com.ratana.task_management_api.exception.DuplicateResourceException;
import com.ratana.task_management_api.exception.ResourceNotFoundException;
import com.ratana.task_management_api.mapper.PositionMapper;
import com.ratana.task_management_api.model.Department;
import com.ratana.task_management_api.model.Position;
import com.ratana.task_management_api.repository.DepartmentRepository;
import com.ratana.task_management_api.repository.PositionRepository;
import com.ratana.task_management_api.service.PositionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;


@Service
@Slf4j
@RequiredArgsConstructor
public class PositionServiceImpl implements PositionService {

    private final PositionRepository positionRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionMapper positionMapper;

    @Override
    @Transactional(readOnly = true)
    public PositionResponse create(PositionRequest request) {

        if (StringUtils.hasText(request.getTitle()) && positionRepository.existsByNameIgnoreCase(request.getTitle())) {
            throw new DuplicateResourceException("Position code already exists: " + request.getTitle());

        }
        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + request.getDepartmentId()));
        Position position = positionMapper.toEntity(request);
        position.setDepartment(department);

        Position saved =  positionRepository.save(position);

        log.info("Position created: {} ({})", saved.getName(), saved.getId());

        return positionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PositionResponse update(UUID id, PositionRequest request) {

        Position position = positionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Position not found"));

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));


        positionMapper.updateEntityFromRequest(request, position);
        position.setDepartment(department);
        return positionMapper.toResponse(positionRepository.save(position));

    }

    @Override
    @Transactional(readOnly = true)
    public PositionResponse getById(UUID id) {
        return positionMapper.toResponse(positionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Position not found")));
    }

    @Override
    public List<PositionResponse> getAll() {

        return positionRepository.findAllActiveWithDepartment()
                .stream()
                .map(positionMapper::toResponse)
                .toList();
    }

    @Override
    public void delete(UUID id) {
        Position position = positionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Position not found"));
        // Soft delete — preserves references from employee records
        position.setActive(false);
        positionRepository.save(position);
        log.info("Position deactivated: {} ({})", position.getName(), id);
    }
}

