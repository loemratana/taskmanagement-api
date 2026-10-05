package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.dto.request.*;
import com.ratana.task_management_api.dto.response.EmployeeHierarchyResponse;
import com.ratana.task_management_api.dto.response.ManagerResponse;
import com.ratana.task_management_api.dto.response.OrgMemberResponse;
import com.ratana.task_management_api.dto.response.PageResponse;
import com.ratana.task_management_api.dto.response.UserResponse;
import com.ratana.task_management_api.exception.DuplicateResourceException;
import com.ratana.task_management_api.exception.InvalidPasswordException;
import com.ratana.task_management_api.exception.InvalidRequestException;
import com.ratana.task_management_api.exception.ResourceNotFoundException;
import com.ratana.task_management_api.mapper.OrgHierarchyMapper;
import com.ratana.task_management_api.mapper.UserMapper;
import com.ratana.task_management_api.model.Department;
import com.ratana.task_management_api.model.Position;
import com.ratana.task_management_api.model.Role;
import com.ratana.task_management_api.model.User;
import com.ratana.task_management_api.repository.DepartmentRepository;
import com.ratana.task_management_api.repository.PositionRepository;
import com.ratana.task_management_api.repository.RoleRepository;
import com.ratana.task_management_api.repository.UserRepository;
import com.ratana.task_management_api.repository.projection.OrgHierarchyRow;
import com.ratana.task_management_api.service.UserService;
import com.ratana.task_management_api.utility.UserSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;


@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpL implements UserService {

    private final UserRepository userRepository;
    private final UserMapper  userMapper;

    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;

    private final PasswordEncoder passwordEncoder;

    private final CloudinaryServiceImpL cloudinaryServiceImpL;

    private final RoleRepository roleRepository;

    private final OrgHierarchyMapper orgHierarchyMapper;
    @Override
    public UserResponse create(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw  new DuplicateResourceException("Email already exists {}"+request.getEmail());
        }

        User user = userMapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));


        if (request.getPositionId() != null) {
            user.setPosition(getPositionOrThrow(request.getPositionId()));
        }


        if (request.getManagerId()!= null) {
            user.setManager(getUserOrThrow(request.getManagerId()));
        }
        if (request.getAvatarUrl()!= null && !request.getAvatarUrl().isEmpty()) {

        }

        user.setRole(getRoleOrThrow(request.getRoleId()));


        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public UserResponse getById(UUID id) {
        return userMapper.toResponse(getUserOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(UserFilterRequest filter, Pageable pageable) {

        return PageResponse.from(userRepository.findAll(UserSpecifications.fromFilter(filter),pageable).map(userMapper::toResponse));
    }

    @Override
    public UserResponse update(UUID id, UserUpdateRequest request) {

        User user = getUserOrThrow(id);
        if (request.getEmail() != null && !user.getEmail().equals(user.getEmail()) && userRepository.existsByEmail(request.getEmail())) {

            throw  new DuplicateResourceException("Email already exists {}"+request.getEmail());
        }

        userMapper.upateEntity(user,request);
        if (request.getRoleId() != null) {
            user.setRole(getRoleOrThrow(request.getRoleId()));
        }
        return userMapper.toResponse(user);
    }

    @Override
    public void deactivate(UUID id) {
        User user = getUserOrThrow(id);
        if (!user.isActive())
        {
            throw  new InvalidRequestException("User has been deactivated");
        }
        user.setActive(false);
        userRepository.save(user);

    }

    @Override
    public void activate(UUID id) {
        User user = getUserOrThrow(id);
        if (user.isActive())
        {
            throw  new InvalidRequestException("User is already active");
        }
        user.setActive(true);
        userRepository.save(user);

    }

    @Override
    @Transactional
    public UserResponse assignRole(UUID id, AssignRoleRequest request) {

        User user = getUserOrThrow(id);

        user.setRole(getRoleOrThrow(request.getRoleId()));
        userRepository.save(user);
        log.info("Assigned role {}", user.getRole().getName());

        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse assignManager(UUID id, AssignManagerRequest request) {

        User user = getUserOrThrow(id);

        if (request.getManagerId() == null)
        {
            user.setManager(null);
            userRepository.save(user);
            return userMapper.toResponse(user);
        }

        if (request.getManagerId().equals(id))
        {
            throw  new InvalidRequestException("Manager Id is already assigned");
        }
        User manager = getUserOrThrow(request.getManagerId());
        if (!manager.isActive())
        {
            throw new InvalidRequestException("Manager is not active");
        }
        // Prevent circular hierarchy
        if (userRepository.wouldCreateManagerCycle(user.getId(), manager.getId())) {
            throw new InvalidRequestException("This assignment would create a manager cycle");
        }
        user.setManager(manager);
        userRepository.save(user);

        return userMapper.toResponse(user);
    }




    @Override
    @Transactional
    public UserResponse assignOrg(UUID id, AssignOrgRequest request) {

        if (request.getDepartmentId() == null && request.getPositionId() == null) {
            throw new InvalidRequestException("Provide a departmentId and/or a positionId");
        }

        User user = getUserOrThrow(id);

        if (request.getDepartmentId() != null) {
            user.setDepartment(getDepartmentOrThrow(request.getDepartmentId()));
        }
        if (request.getPositionId() != null) {
            user.setPosition(getPositionOrThrow(request.getPositionId()));
        }

        userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Override
    public void changePassword(UUID id, ChangePasswordRequest request) {

        User user =  getUserOrThrow(id);
        if (user.getPasswordHash() ==null || !passwordEncoder.matches(request.getCurrentPassword(),user.getPasswordHash())) {
            throw new InvalidPasswordException("Current password is incorrect");

        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));

    }

    @Override
    @Transactional
    public UserResponse uploadAvatar(UUID id, MultipartFile file) {

        User user = getUserOrThrow(id);
        if (  file ==null ||file.isEmpty()) {
            throw new InvalidRequestException("Avatar file is required");
        }
        String avatarUrl = cloudinaryServiceImpL.uploadImage(file);
        user.setAvatarUrl(avatarUrl);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public void removeAvatar(UUID id) {

        User user = getUserOrThrow(id);

        user.setAvatarUrl(null);
        userRepository.save(user);

    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getDirectReports(UUID managerId) {
        if (!userRepository.existsById(managerId)) {
            throw new ResourceNotFoundException("User not found: " + managerId);
        }
        return userRepository.findUserByManagerId(managerId).stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ManagerResponse getManager(UUID id) {
        User user = getUserOrThrow(id);
        return user.getManager() == null ? null : userMapper.toManagerResponse(user.getManager());
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeHierarchyResponse getHierarchy(UUID id) {
        List<OrgHierarchyRow> chainRows = userRepository.findManagementChain(id);
        OrgHierarchyRow self = chainRows.stream()
                .filter(row -> row.getLevel() == 0)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        int depth = chainRows.stream().mapToInt(OrgHierarchyRow::getLevel).max().orElse(0);
        List<OrgMemberResponse> managementChain = chainRows.stream()
                .filter(row -> row.getLevel() > 0)
                .map(orgHierarchyMapper::toMember)
                .toList();

        List<OrgHierarchyRow> subtreeRows = userRepository.findSubtreeUnderManager(id);

        return EmployeeHierarchyResponse.builder()
                .id(self.getId())
                .fullName(self.getFullName())
                .avatarUrl(self.getAvatarUrl())
                .level(depth)
                .managementChain(managementChain)
                .subordinates(orgHierarchyMapper.toMembers(subtreeRows))
                .build();
    }

    // --- helpers -----------------------------------------------------------

    private User getUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }
    private Position getPositionOrThrow(UUID id)
    {
        return positionRepository.findById(id).orElseThrow(() ->new ResourceNotFoundException("Position not found with {}"+id));

    }

    private Department getDepartmentOrThrow(UUID id)
    {
        return departmentRepository.findById(id).orElseThrow(() ->new ResourceNotFoundException("Position not found with {}"+id));

    }

    private Role getRoleOrThrow(UUID id)
    {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + id));
        if (!role.isActive()) {
            throw new InvalidRequestException("Role has been inactive");
        }
        return role;
    }
}
