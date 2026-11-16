package com.ratana.task_management_api.booking.service;

import com.ratana.task_management_api.booking.dto.request.BookingTargetsRequest;
import com.ratana.task_management_api.common.exception.BadRequestException;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.organization.entity.Position;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.organization.repository.DepartmentRepository;
import com.ratana.task_management_api.organization.repository.PositionRepository;
import com.ratana.task_management_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Loads the selected departments/positions/users and turns them into the attendee list. */
@Component
@RequiredArgsConstructor
public class BookingAudienceResolver {

    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final UserRepository userRepository;

    /**
     * @param departments selected departments
     * @param positions   selected positions (each belongs to a department)
     * @param users       individually selected users, loaded with their department
     * @param attendeeIds union of all enabled users reached by the targets, organizer included
     */
    public record ResolvedAudience(Set<Department> departments,
                                   Set<Position> positions,
                                   Set<User> users,
                                   Set<UUID> attendeeIds) {
        public int attendeeCount() {
            return attendeeIds.size();
        }
    }

    public ResolvedAudience resolve(BookingTargetsRequest targets, UUID organizerId) {
        BookingTargetsRequest t = targets != null ? targets : BookingTargetsRequest.empty();
        Set<UUID> departmentIds = nonNull(t.getDepartmentIds());
        Set<UUID> positionIds = nonNull(t.getPositionIds());
        Set<UUID> userIds = nonNull(t.getUserIds());

        Set<Department> departments = loadDepartments(departmentIds);
        Set<Position> positions = loadPositions(positionIds);
        Set<User> users = loadUsers(userIds);

        Set<UUID> attendeeIds = new LinkedHashSet<>();
        attendeeIds.add(organizerId);
        if (!departmentIds.isEmpty()) {
            attendeeIds.addAll(userRepository.findEnabledUserIdsByDepartmentIds(departmentIds));
        }
        if (!positionIds.isEmpty()) {
            attendeeIds.addAll(userRepository.findEnabledUserIdsByPositionIds(positionIds));
        }
        users.forEach(u -> attendeeIds.add(u.getId()));

        return new ResolvedAudience(departments, positions, users, attendeeIds);
    }

    private Set<Department> loadDepartments(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return new HashSet<>();
        }
        List<Department> found = departmentRepository.findAllById(ids);
        requireAll(ids, found.stream().map(Department::getId).toList(), "Department");
        found.stream().filter(d -> !d.isActive()).findFirst().ifPresent(d -> {
            throw new BadRequestException("Department '%s' is inactive".formatted(d.getName()));
        });
        return new HashSet<>(found);
    }

    private Set<Position> loadPositions(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return new HashSet<>();
        }
        List<Position> found = positionRepository.findAllById(ids);
        requireAll(ids, found.stream().map(Position::getId).toList(), "Position");
        found.stream().filter(p -> !p.isActive()).findFirst().ifPresent(p -> {
            throw new BadRequestException("Position '%s' is inactive".formatted(p.getName()));
        });
        return new HashSet<>(found);
    }

    private Set<User> loadUsers(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return new HashSet<>();
        }
        List<User> found = userRepository.findAllWithDepartmentByIdIn(ids);
        requireAll(ids, found.stream().map(User::getId).toList(), "User");
        found.stream().filter(u -> !u.isAccountEnabled()).findFirst().ifPresent(u -> {
            throw new BadRequestException("User '%s' is not active and cannot be invited".formatted(u.getFullName()));
        });
        return new HashSet<>(found);
    }

    private static void requireAll(Set<UUID> requested, Collection<UUID> found, String kind) {
        if (found.size() == requested.size()) {
            return;
        }
        Set<UUID> missing = requested.stream().filter(id -> !found.contains(id)).collect(Collectors.toSet());
        throw new BadRequestException("%s not found: %s".formatted(kind, missing));
    }

    private static Set<UUID> nonNull(Set<UUID> ids) {
        if (ids == null) {
            return Set.of();
        }
        return ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
