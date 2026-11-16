package com.ratana.task_management_api.organization.service;

import com.ratana.task_management_api.organization.dto.request.DepartmentRequest;
import com.ratana.task_management_api.organization.dto.response.DepartmentMemberResponse;
import com.ratana.task_management_api.organization.dto.response.DepartmentResponse;
import com.ratana.task_management_api.organization.dto.response.DepartmentTreeResponse;
import com.ratana.task_management_api.common.exception.BadRequestException;
import com.ratana.task_management_api.common.exception.DuplicateResourceException;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.organization.mapper.DepartmentMapper;
import com.ratana.task_management_api.user.mapper.UserMapper;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.organization.entity.Position;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.organization.repository.DepartmentRepository;
import com.ratana.task_management_api.organization.repository.PositionRepository;
import com.ratana.task_management_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@Slf4j
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;

    private final DepartmentMapper departmentMapper;

    private final UserRepository userRepository;

    private final UserMapper userMapper;

    private final PositionRepository positionRepository;

    @Override
    public DepartmentResponse create(DepartmentRequest request) {

        if (StringUtils.hasText(request.getName()) && departmentRepository.existsByNameIgnoreCase(request.getName())) {
            log.warn("Department creation rejected: name {} already exists", request.getName());
            throw  new DuplicateResourceException("Department name already exists:"+ request.getName());
        }
        Department department = departmentMapper.toEntity(request);

        if (request.getManagerId() != null) {
            User manager = userRepository.findById(request.getManagerId())
                    .orElseThrow(() -> {
                        log.warn("Department creation rejected: manager {} not found", request.getManagerId());
                        return new ResourceNotFoundException("Manager not found: " + request.getManagerId());
                    });

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
            log.warn("Department {} update rejected: name {} already exists", id, request.getName());
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
        log.debug("Fetching department {}", id);
        Department department  = departmentRepository.findById(id).orElseThrow(()-> new ResourceNotFoundException(""));
        return departmentMapper.toResponse(department);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentTreeResponse> getTree() {
        log.debug("Building department tree");

        // Three queries total, grouped in memory — no per-department lookups.
        Map<UUID, List<User>> membersByDepartment = userRepository.findAllForOrgChart().stream()
                .filter(User::isActive)
                .filter(user -> user.getDepartment() != null)
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.groupingBy(user -> user.getDepartment().getId()));
        Map<UUID, List<Position>> positionsByDepartment = positionRepository.findAllActiveWithDepartment().stream()
                .collect(Collectors.groupingBy(position -> position.getDepartment().getId()));

        return departmentRepository.findAllByOrderByNameAsc().stream()
                .filter(Department::isActive)
                .map(department -> toTreeNode(
                        department,
                        positionsByDepartment.getOrDefault(department.getId(), List.of()),
                        membersByDepartment.getOrDefault(department.getId(), List.of())))
                .toList();
    }

    private DepartmentTreeResponse toTreeNode(Department department, List<Position> positions, List<User> members) {

        Map<UUID, Position> positionsById = new HashMap<>();
        positions.forEach(position -> positionsById.put(position.getId(), position));

        Map<UUID, List<DepartmentMemberResponse>> membersByPosition = new HashMap<>();
        List<DepartmentMemberResponse> unassignedMembers = new ArrayList<>();
        for (User member : members)
        {
            DepartmentMemberResponse response = userMapper.toMemberResponse(member);
            Position position = member.getPosition();
            if (position == null)
            {
                unassignedMembers.add(response);
                continue;
            }
            // A member may hold a position from another department or an inactive one — still show them.
            positionsById.putIfAbsent(position.getId(), position);
            membersByPosition.computeIfAbsent(position.getId(), key -> new ArrayList<>()).add(response);
        }

        List<DepartmentTreeResponse.PositionNode> positionNodes = positionsById.values().stream()
                .sorted(Comparator.comparing(Position::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(position -> DepartmentTreeResponse.PositionNode.builder()
                        .id(position.getId())
                        .name(position.getName())
                        .members(membersByPosition.getOrDefault(position.getId(), List.of()))
                        .build())
                .toList();

        return DepartmentTreeResponse.builder()
                .id(department.getId())
                .name(department.getName())
                .description(department.getDescription())
                .manager(departmentMapper.toManagerResponse(department.getManager()))
                .memberCount(members.size())
                .positions(positionNodes)
                .unassignedMembers(unassignedMembers)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getAllFlat() {

        List<Department> department=  departmentRepository.findAllByOrderByNameAsc();
        log.debug("Fetched {} department(s)", department.size());
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
        User manager = userRepository.findByIdWithRoles(managerId)
                .orElseThrow(() -> {
                    log.warn("Manager assignment for department {} rejected: user {} not found", departmentId, managerId);
                    return new ResourceNotFoundException("Manager not found with id: " + managerId);
                });

        Set<String> allowedRoles = Set.of("MANAGER", "ADMIN");

        boolean hasManagerRole  = manager.getRoles().stream().anyMatch(role -> allowedRoles.contains(role.getName()));
        if (!hasManagerRole) {
            log.warn("Manager assignment for department {} rejected: user {} lacks ADMIN/MANAGER role", departmentId, managerId);
            throw new BadRequestException(
                    "User must have ADMIN or MANAGER role to be assigned as department manager"
            );        }
        department.setManager(manager);
        Department saved = departmentRepository.save(department);
        log.info("Manager {} ({}) assigned to department {} ({})",
                manager.getEmail(), managerId, saved.getName(), departmentId);

        return departmentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentMemberResponse> getMembers(UUID departmentId) {

        if (!departmentRepository.existsById(departmentId)) {
            log.warn("Department not found: {}", departmentId);
            throw new ResourceNotFoundException("Department not found with id:" + departmentId);
        }
        log.debug("Fetching members of department {}", departmentId);

        return userRepository.findByDepartmentId(departmentId).stream()
                .filter(User::isActive)
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(userMapper::toMemberResponse)
                .toList();
    }
}
