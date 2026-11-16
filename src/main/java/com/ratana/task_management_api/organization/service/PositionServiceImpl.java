package com.ratana.task_management_api.organization.service;

import com.ratana.task_management_api.organization.dto.request.PositionRequest;
import com.ratana.task_management_api.organization.dto.response.PositionResponse;
import com.ratana.task_management_api.common.exception.DuplicateResourceException;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.organization.mapper.PositionMapper;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.organization.entity.Position;
import com.ratana.task_management_api.organization.repository.DepartmentRepository;
import com.ratana.task_management_api.organization.repository.PositionRepository;
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
    @Transactional
    public PositionResponse create(PositionRequest request) {

        if (StringUtils.hasText(request.getTitle()) && positionRepository.existsByNameIgnoreCase(request.getTitle())) {
            log.warn("Position creation rejected: {} already exists", request.getTitle());
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
        Position saved = positionRepository.save(position);
        log.info("Position updated: {} ({})", saved.getName(), id);
        return positionMapper.toResponse(saved);

    }

    @Override
    @Transactional(readOnly = true)
    public PositionResponse getById(UUID id) {
        log.debug("Fetching position {}", id);
        return positionMapper.toResponse(positionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Position not found")));
    }

    @Override
    public List<PositionResponse> getAll() {
        log.debug("Fetching all active positions");

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

