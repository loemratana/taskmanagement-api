package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.dto.request.DepartmentRequest;
import com.ratana.task_management_api.dto.response.DepartmentMemberResponse;
import com.ratana.task_management_api.dto.response.DepartmentResponse;
import com.ratana.task_management_api.dto.response.DepartmentTreeResponse;
import com.ratana.task_management_api.exception.BadRequestException;
import com.ratana.task_management_api.exception.DuplicateResourceException;
import com.ratana.task_management_api.exception.ResourceNotFoundException;
import com.ratana.task_management_api.mapper.DepartmentMapper;
import com.ratana.task_management_api.mapper.UserMapper;
import com.ratana.task_management_api.model.Department;
import com.ratana.task_management_api.model.User;
import com.ratana.task_management_api.repository.DepartmentRepository;
import com.ratana.task_management_api.repository.UserRepository;
import com.ratana.task_management_api.security.role.Roles;
import com.ratana.task_management_api.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;


@Service
@Slf4j
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;

    private final DepartmentMapper departmentMapper;

    private final UserRepository userRepository;

    private final UserMapper userMapper;

    @Override
    public DepartmentResponse create(DepartmentRequest request) {

        if (StringUtils.hasText(request.getName()) && departmentRepository.existsByNameIgnoreCase(request.getName())) {
            throw  new DuplicateResourceException("Department name already exists:"+ request.getName());
        }
        Department department = departmentMapper.toEntity(request);

        if (request.getManagerId() != null) {
            User manager = userRepository.findById(request.getManagerId())
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "Manager not found: " + request.getManagerId()
                            )
                    );

            department.setManager(manager);
        }
        else {
            department.setManager(null);
        }
        Department saved = departmentRepository.save(department);

        log.info(
                "Department created successfully. id={}, name={}, manager={}",
                saved.getId(),
                saved.getName(),
                saved.getManager() != null
                        ? saved.getManager().getEmail()
                        : "NONE"
        );

        return departmentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DepartmentResponse update(UUID id, DepartmentRequest request) {

        Department department = departmentRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Department not found with id:"+id));
        if (StringUtils.hasText(request.getName()) && departmentRepository.existsByNameIgnoreCase(request.getName())) {
            throw  new DuplicateResourceException("Department name already exists:"+ request.getName());
        }
        departmentMapper.updateEntityFromRequest(request, department);
       if (request.getManagerId() != null) {
           department.setManager(userRepository.findById(request.getManagerId()).get());
       }
        Department saved = departmentRepository.save(department);
        log.info("Department updated: {} ({})", saved.getName(), saved.getId());
        return departmentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentResponse getById(UUID id) {
        Department department  = departmentRepository.findById(id).orElseThrow(()-> new ResourceNotFoundException(""));
        return departmentMapper.toResponse(department);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentTreeResponse> getTree() {
        return departmentMapper.toTreeResponseList(departmentRepository.findAllByOrderByNameAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getAllFlat() {

        List<Department> department=  departmentRepository.findAllByOrderByNameAsc();
        return departmentMapper.toResponseList(department);
    }

    @Override
    public void delete(UUID id) {

        Department department = departmentRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("DEPARTMENT not found with id:"+id));
        department.setActive(false);
        departmentRepository.save(department);
        log.info("Department deleted: {} ({})", department.getName(), department.getId());

    }

    @Override
    @Transactional
    public DepartmentResponse assignManager(UUID departmentId, UUID managerId) {

        Department department = departmentRepository.findById(departmentId).orElseThrow(()->new ResourceNotFoundException("Department not found with id:"+departmentId));
        User manager = userRepository.findByIdWithRole(managerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Manager not found with id: " + managerId
                        )
                );

        Set<String> allowedRoles = Set.of(Roles.MANAGER, Roles.ADMIN);

        boolean hasManagerRole = manager.getRole() != null && allowedRoles.contains(manager.getRole().getName());
        if (!hasManagerRole) {
            throw new BadRequestException(
                    "User must have ADMIN or MANAGER role to be assigned as department manager"
            );        }
        department.setManager(manager);
        Department saved = departmentRepository.save(department);

        return departmentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentMemberResponse> getMembers(UUID departmentId) {

        if (!departmentRepository.existsById(departmentId)) {
            throw new ResourceNotFoundException("Department not found with id:" + departmentId);
        }

        return userRepository.findByDepartmentId(departmentId).stream()
                .filter(User::isActive)
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(userMapper::toMemberResponse)
                .toList();
    }
}
