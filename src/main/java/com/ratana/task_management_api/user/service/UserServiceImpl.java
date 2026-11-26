package com.ratana.task_management_api.user.service;

import com.ratana.task_management_api.common.cache.AppCaches;

import com.ratana.task_management_api.auth.dto.request.ChangePasswordRequest;
import com.ratana.task_management_api.rbac.dto.request.AssignRolesRequest;
import com.ratana.task_management_api.user.dto.request.AssignManagerRequest;
import com.ratana.task_management_api.user.dto.request.AssignOrgRequest;
import com.ratana.task_management_api.user.dto.request.UserCreateRequest;
import com.ratana.task_management_api.user.dto.request.UserFilterRequest;
import com.ratana.task_management_api.user.dto.request.UserUpdateRequest;

import com.ratana.task_management_api.file.config.AttachmentUploadPolicy;

import com.ratana.task_management_api.organization.dto.response.EmployeeHierarchyResponse;
import com.ratana.task_management_api.user.dto.response.ManagerResponse;
import com.ratana.task_management_api.organization.dto.response.OrgMemberResponse;
import com.ratana.task_management_api.organization.dto.response.OrgTreeNodeResponse;
import com.ratana.task_management_api.common.pagination.PageResponse;
import com.ratana.task_management_api.common.response.Summary;
import com.ratana.task_management_api.user.dto.response.UserResponse;
import com.ratana.task_management_api.user.enums.UserStatus;
import com.ratana.task_management_api.common.exception.DuplicateResourceException;
import com.ratana.task_management_api.auth.exception.InvalidPasswordException;
import com.ratana.task_management_api.common.exception.InvalidRequestException;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.user.mapper.UserMapper;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.organization.entity.Position;
import com.ratana.task_management_api.rbac.entity.Role;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.organization.repository.DepartmentRepository;
import com.ratana.task_management_api.organization.repository.PositionRepository;
import com.ratana.task_management_api.rbac.repository.RoleRepository;
import com.ratana.task_management_api.user.repository.UserRepository;
import com.ratana.task_management_api.file.exception.UnsupportedFileTypeException;
import com.ratana.task_management_api.auth.service.RefreshTokenService;
import com.ratana.task_management_api.infrastructure.storage.StorageService;
import com.ratana.task_management_api.user.specification.UserSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String AVATAR_DIRECTORY = "avatars";

    private final UserRepository userRepository;
    private final UserMapper  userMapper;

    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;

    private final PasswordEncoder passwordEncoder;

    private final StorageService storageService;

    private final RoleRepository roleRepository;

    private final RefreshTokenService refreshTokenService;
    private final AppCaches caches;
    @Override
    public UserResponse create(UserCreateRequest request) {
        log.debug("Creating user with email {}", request.getEmail());
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("User creation rejected: email {} already exists", request.getEmail());
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

        Role employeeRole = roleRepository.findByNameIgnoreCase("EMPLOYEE")
                .orElseThrow(() ->
                        new ResourceNotFoundException("EMPLOYEE role not found"));

        user.setRoles(Set.of(employeeRole));

        User saved = userRepository.save(user);
        log.info("User created: {} ({})", saved.getEmail(), saved.getId());
        return userMapper.toResponse(saved);
    }

    @Override
    public UserResponse getById(UUID id) {
        log.debug("Fetching user {}", id);
        return userMapper.toResponse(getUserOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(UserFilterRequest filter, Pageable pageable) {
        log.debug("Searching users. filter={}, page={}, size={}", filter, pageable.getPageNumber(), pageable.getPageSize());
        return PageResponse.from(userRepository.findAll(UserSpecifications.fromFilter(filter),pageable).map(userMapper::toResponse));
    }

    @Override
    @Transactional
    public UserResponse update(UUID id, UserUpdateRequest request) {

        User user = getUserOrThrow(id);
        String newEmail = request.getEmail() == null ? null : request.getEmail().trim();
        if (StringUtils.hasText(newEmail) && !newEmail.equalsIgnoreCase(user.getEmail())
                && userRepository.existsByEmail(newEmail)) {

            log.warn("User {} update rejected: email {} already exists", id, newEmail);
            throw  new DuplicateResourceException("Email already exists: " + newEmail);
        }

        userMapper.upateEntity(user,request);
        User saved = userRepository.save(user);
        // Cache-aside: database written above; drop cached copies once it commits.
        caches.principals().invalidateAll();
        caches.departments().invalidateAll();
        log.info("User updated: {} ({})", saved.getEmail(), id);
        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void deactivate(UUID id) {
        User user = getUserOrThrow(id);
        if (!user.isAccountEnabled())
        {
            log.warn("Deactivate rejected: user {} is already inactive", id);
            throw  new InvalidRequestException("User has been deactivated");
        }
        // status is what login and the JWT filter check; keep is_active in step with it.
        user.setStatus(UserStatus.INACTIVE);
        user.setActive(false);
        userRepository.save(user);
        // Cache-aside: database written above; drop cached copies once it commits.
        caches.principals().invalidateAll();
        caches.departments().invalidateAll();
        // End every session now instead of letting refresh tokens live out their 7 days.
        refreshTokenService.revokeAllForUser(id);
        log.info("User deactivated and signed out everywhere: {} ({})", user.getEmail(), id);
    }

    @Override
    @Transactional
    public void activate(UUID id) {
        User user = getUserOrThrow(id);
        if (user.isAccountEnabled())
        {
            log.warn("Activate rejected: user {} is already active", id);
            throw  new InvalidRequestException("User is already active");
        }
        user.setStatus(UserStatus.ACTIVE);
        user.setActive(true);
        userRepository.save(user);
        // Cache-aside: database written above; drop cached copies once it commits.
        caches.principals().invalidateAll();
        caches.departments().invalidateAll();
        log.info("User activated: {} ({})", user.getEmail(), id);
    }

    @Override
    @Transactional
    public UserResponse assignRoles(UUID id, AssignRolesRequest request) {

        User user = getUserOrThrow(id);

        if (request.getRoleIds() == null || request.getRoleIds().isEmpty()) {
            throw new InvalidRequestException(
                    "At least one role ID is required"
            );
        }
        Set<Role> roles = new HashSet<>(
                roleRepository.findAllById(
                        request.getRoleIds()
                )
        );
        if (roles.size() != request.getRoleIds().size()) {
            log.warn("Role assignment for user {} rejected: unknown role id in {}", id, request.getRoleIds());
            throw new InvalidRequestException("One or more role IDs do not exist");
        }
        boolean hasInactiveRole = roles.stream().anyMatch(role -> !role.isActive());
        if (hasInactiveRole)
        {
            log.warn("Role assignment for user {} rejected: inactive role in {}", id, request.getRoleIds());
            throw new  InvalidRequestException("Role has been inactive");
        }

        user.setRoles(roles);
        userRepository.save(user);
        // Cache-aside: database written above; drop cached copies once it commits.
        caches.principals().invalidateAll();
        caches.departments().invalidateAll();
        log.info("Roles {} assigned to user {}", roles.stream().map(Role::getName).toList(), id);

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
            log.info("Manager removed from user {}", id);
            return userMapper.toResponse(user);
        }

        if (request.getManagerId().equals(id))
        {
            log.warn("Manager assignment rejected: user {} cannot manage themselves", id);
            throw  new InvalidRequestException("A user cannot be their own manager");
        }
        User manager = getUserOrThrow(request.getManagerId());
        if (!manager.isActive())
        {
            log.warn("Manager assignment for user {} rejected: manager {} is inactive", id, manager.getId());
            throw new InvalidRequestException("Manager is not active");
        }
        // Prevent circular hierarchy
        assertNoManagerCycle(user, manager);
        user.setManager(manager);
        userRepository.save(user);
        log.info("Manager {} assigned to user {}", manager.getId(), id);
        return userMapper.toResponse(user);
    }




    @Override
    @Transactional
    public UserResponse assignOrg(UUID id, AssignOrgRequest request) {

        User user = getUserOrThrow(id);

        Department department = null;
        if (request.getDepartmentId() != null)
        {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + request.getDepartmentId()));
            if (!department.isActive())
            {
                log.warn("Org assignment for user {} rejected: department {} is inactive", id, department.getId());
                throw new InvalidRequestException("Department is not active");
            }
        }

        Position position = null;
        if (request.getPositionId() != null)
        {
            position = getPositionOrThrow(request.getPositionId());
            if (!position.isActive())
            {
                log.warn("Org assignment for user {} rejected: position {} is inactive", id, position.getId());
                throw new InvalidRequestException("Position is not active");
            }
            Department positionDepartment = position.getDepartment();
            if (department == null)
            {
                // Position implies its department when none is given explicitly
                department = positionDepartment;
            }
            else if (positionDepartment != null && !positionDepartment.getId().equals(department.getId()))
            {
                log.warn("Org assignment for user {} rejected: position {} is not in department {}",
                        id, position.getId(), department.getId());
                throw new InvalidRequestException("Position does not belong to the given department");
            }
        }

        user.setDepartment(department);
        user.setPosition(position);
        userRepository.save(user);
        // Cache-aside: database written above; drop cached copies once it commits.
        caches.principals().invalidateAll();
        caches.departments().invalidateAll();
        log.info("Org assigned to user {}: department={}, position={}", id,
                department == null ? null : department.getId(), position == null ? null : position.getId());
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public void changePassword(UUID id, ChangePasswordRequest request) {

        User user =  getUserOrThrow(id);
        if (user.getPasswordHash() ==null || !passwordEncoder.matches(request.getCurrentPassword(),user.getPasswordHash())) {
            log.warn("Password change rejected for user {}: current password is incorrect", id);
            throw new InvalidPasswordException("Current password is incorrect");

        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            log.warn("Password change rejected for user {}: new password equals the current one", id);
            throw new InvalidRequestException("New password must be different from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for user {}", id);
    }

    @Override
    @Transactional
    public UserResponse uploadAvatar(UUID id, MultipartFile file) {

        User user = getUserOrThrow(id);
        if (  file ==null ||file.isEmpty()) {
            log.warn("Avatar upload for user {} rejected: file is empty", id);
            throw new InvalidRequestException("Avatar file is required");
        }
        String contentType = file.getContentType();
        if (!AttachmentUploadPolicy.isImage(file)) {
            log.warn("Avatar upload for user {} rejected: unsupported content type {}", id, contentType);
            throw new UnsupportedFileTypeException("Only image files can be uploaded, got: " + contentType);
        }

        // avatars/ is anonymously readable (see MinioConfig), so a permanent public URL is stored.
        String storageKey = storageService.upload(file, AVATAR_DIRECTORY).getStorageKey();
        String previousUrl = user.getAvatarUrl();
        user.setAvatarUrl(storageService.publicUrl(storageKey));
        UserResponse response = userMapper.toResponse(userRepository.save(user));
        // Cache-aside: database written above; drop cached copies once it commits.
        caches.principals().invalidateAll();
        caches.departments().invalidateAll();
        log.info("Avatar uploaded for user {}: key={}, size={}", id, storageKey, file.getSize());
        deleteStoredAvatar(previousUrl);
        return response;
    }

    @Override
    public void removeAvatar(UUID id) {

        User user = getUserOrThrow(id);
        String previousUrl = user.getAvatarUrl();

        user.setAvatarUrl(null);
        userRepository.save(user);
        // Cache-aside: database written above; drop cached copies once it commits.
        caches.principals().invalidateAll();
        caches.departments().invalidateAll();
        log.info("Avatar removed for user {}", id);
        deleteStoredAvatar(previousUrl);

    }

    /** Best-effort cleanup of an avatar we stored in MinIO; external URLs are left alone. */
    private void deleteStoredAvatar(String avatarUrl) {
        String prefix = storageService.publicUrl("");
        if (avatarUrl == null || !avatarUrl.startsWith(prefix + AVATAR_DIRECTORY + "/")) {
            return;
        }
        try {
            storageService.delete(avatarUrl.substring(prefix.length()));
            log.debug("Old avatar deleted from storage: {}", avatarUrl);
        } catch (Exception e) {
            log.warn("Could not delete old avatar {}: {}", avatarUrl, e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getDirectReports(UUID managerId) {
        log.debug("Fetching direct reports of manager {}", managerId);
        getUserOrThrow(managerId);
        return userRepository.findUserByManagerId(managerId).stream()
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ManagerResponse getManager(UUID id) {
        log.debug("Fetching manager of user {}", id);
        User manager = getUserOrThrow(id).getManager();
        if (manager == null)
        {
            return null;
        }
        return new ManagerResponse(manager.getId(), manager.getFullName(), manager.getEmail(), manager.getAvatarUrl());
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeHierarchyResponse getHierarchy(UUID id) {

        // Inactive users are kept here: the reporting line is shown as it is recorded.
        List<User> users = userRepository.findAllForOrgChart();
        Map<UUID, User> usersById = users.stream().collect(Collectors.toMap(User::getId, Function.identity()));
        User subject = usersById.get(id);
        if (subject == null)
        {
            log.warn("Hierarchy requested for unknown user {}", id);
            throw new ResourceNotFoundException("User not found: " + id);
        }

        // Walk up: direct manager first, top-level last. visited guards against bad data cycles.
        List<OrgMemberResponse> managementChain = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        visited.add(id);
        User current = usersById.get(managerIdOf(subject));
        while (current != null && visited.add(current.getId()))
        {
            managementChain.add(toOrgMember(current, managementChain.size() + 1));
            current = usersById.get(managerIdOf(current));
        }

        // Walk down breadth-first so subordinates come out grouped by level.
        Map<UUID, List<User>> reportsByManager = groupByManager(users);
        List<OrgMemberResponse> subordinates = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        seen.add(id);
        List<User> frontier = List.of(subject);
        int level = 0;
        while (!frontier.isEmpty())
        {
            level++;
            List<User> next = new ArrayList<>();
            for (User manager : frontier)
            {
                for (User report : reportsByManager.getOrDefault(manager.getId(), List.of()))
                {
                    if (seen.add(report.getId()))
                    {
                        subordinates.add(toOrgMember(report, level));
                        next.add(report);
                    }
                }
            }
            frontier = next;
        }

        log.debug("Hierarchy built for user {}: {} manager(s) above, {} subordinate(s)",
                id, managementChain.size(), subordinates.size());
        return EmployeeHierarchyResponse.builder()
                .id(subject.getId())
                .fullName(subject.getFullName())
                .level(managementChain.size())
                .managementChain(managementChain)
                .subordinates(subordinates)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrgTreeNodeResponse> getOrganizationTree() {
        log.debug("Building organization tree");

        List<User> activeUsers = userRepository.findAllForOrgChart().stream()
                .filter(User::isActive)
                .toList();
        Set<UUID> activeIds = activeUsers.stream().map(User::getId).collect(Collectors.toSet());
        Map<UUID, List<User>> reportsByManager = groupByManager(activeUsers);

        // Roots: no manager, or a manager who is no longer active (so their reports are not lost).
        Set<UUID> placed = new HashSet<>();
        return activeUsers.stream()
                .filter(user -> !activeIds.contains(managerIdOf(user)))
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(root -> toTreeNode(root, 0, reportsByManager, placed))
                .toList();
    }

    // --- helpers -----------------------------------------------------------

    private static UUID managerIdOf(User user) {
        return user.getManager() == null ? null : user.getManager().getId();
    }

    private static Map<UUID, List<User>> groupByManager(List<User> users) {
        Map<UUID, List<User>> reportsByManager = new HashMap<>();
        for (User user : users)
        {
            UUID managerId = managerIdOf(user);
            if (managerId != null)
            {
                reportsByManager.computeIfAbsent(managerId, key -> new ArrayList<>()).add(user);
            }
        }
        reportsByManager.values().forEach(reports ->
                reports.sort(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER)));
        return reportsByManager;
    }

    private OrgTreeNodeResponse toTreeNode(User user, int level, Map<UUID, List<User>> reportsByManager, Set<UUID> placed) {
        placed.add(user.getId());
        List<OrgTreeNodeResponse> children = reportsByManager.getOrDefault(user.getId(), List.of()).stream()
                .filter(report -> !placed.contains(report.getId()))
                .map(report -> toTreeNode(report, level + 1, reportsByManager, placed))
                .toList();
        return OrgTreeNodeResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .position(toSummary(user.getPosition()))
                .department(toSummary(user.getDepartment()))
                .level(level)
                .children(children)
                .build();
    }

    private static OrgMemberResponse toOrgMember(User user, int level) {
        return OrgMemberResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .position(toSummary(user.getPosition()))
                .department(toSummary(user.getDepartment()))
                .managerId(managerIdOf(user))
                .level(level)
                .build();
    }

    private static Summary toSummary(Position position) {
        return position == null ? null : Summary.builder().id(position.getId()).name(position.getName()).build();
    }

    private static Summary toSummary(Department department) {
        return department == null ? null : Summary.builder().id(department.getId()).name(department.getName()).build();
    }

    private User getUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("User not found: {}", id);
                    return new ResourceNotFoundException("User not found: " + id);
                });
    }
    private Position getPositionOrThrow(UUID id)
    {
        return positionRepository.findById(id).orElseThrow(() ->new ResourceNotFoundException("Position not found with {}"+id));

    }

    private void assertNoManagerCycle(User user,User prospectiveManager)
    {
        User current = prospectiveManager;
        while (current !=null)
        {
            if (current.getId().equals(user.getId()))
            {
                log.warn("Manager assignment rejected: assigning {} to {} would create a cycle",
                        prospectiveManager.getId(), user.getId());
                throw  new InvalidRequestException("This assignment would create a manager cycle");
            }
            current = current.getManager();
        }
    }

    private Department getDepartmentOrThrow(UUID id)
    {
        return departmentRepository.findById(id).orElseThrow(() ->new ResourceNotFoundException("Position not found with {}"+id));

    }
}
