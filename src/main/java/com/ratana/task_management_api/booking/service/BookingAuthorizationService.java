package com.ratana.task_management_api.booking.service;

import com.ratana.task_management_api.booking.exception.BookingScopeException;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.booking.entity.MeetingBooking;
import com.ratana.task_management_api.organization.entity.Position;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.booking.repository.UserBookingScopeRepository;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.booking.service.BookingAudienceResolver.ResolvedAudience;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Booking authorization.
 * <ul>
 *   <li><b>RBAC</b> — what a user may do at all ({@code bookings:create}, {@code bookings:view:all},
 *       {@code bookings:manage:any}) comes from role permissions, checked on the controller.</li>
 *   <li><b>ABAC</b> — whom a user may invite depends on their attributes: own department,
 *       direct reports, and departments an admin assigned in {@code user_booking_scopes}.</li>
 * </ul>
 * Invite scopes (a user may hold several; they add up):
 * <pre>
 *   bookings:invite:all        any department, position or user
 *   bookings:invite:assigned   own department + admin-assigned departments
 *   bookings:invite:department own department
 *   bookings:invite:team       direct reports only (individual users)
 *   none of the above          only the organizer themself
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class BookingAuthorizationService {

    public static final String INVITE_ALL = "bookings:invite:all";
    public static final String INVITE_ASSIGNED = "bookings:invite:assigned";
    public static final String INVITE_DEPARTMENT = "bookings:invite:department";
    public static final String INVITE_TEAM = "bookings:invite:team";
    public static final String VIEW_ALL = "bookings:view:all";
    public static final String MANAGE_ANY = "bookings:manage:any";

    private final UserBookingScopeRepository scopeRepository;

    /** Throws {@link BookingScopeException} listing every target the actor may not invite. */
    public void checkCanInvite(UserPrincipal actor, ResolvedAudience audience) {
        List<String> violations = scopeViolations(actor, audience);
        if (!violations.isEmpty()) {
            throw new BookingScopeException("You are not allowed to invite: " + String.join("; ", violations));
        }
    }

    /** Human-readable list of targets outside the actor's invite scope; empty when all are allowed. */
    public List<String> scopeViolations(UserPrincipal actor, ResolvedAudience audience) {
        if (actor.hasPermission(INVITE_ALL)) {
            return List.of();
        }
        Set<UUID> allowedDepartments = allowedDepartmentIds(actor);
        boolean team = actor.hasPermission(INVITE_TEAM);
        List<String> violations = new ArrayList<>();

        for (Department department : audience.departments()) {
            if (!allowedDepartments.contains(department.getId())) {
                violations.add("department '%s'".formatted(department.getName()));
            }
        }
        for (Position position : audience.positions()) {
            UUID departmentId = position.getDepartment() != null ? position.getDepartment().getId() : null;
            if (departmentId == null || !allowedDepartments.contains(departmentId)) {
                violations.add("position '%s'".formatted(position.getName()));
            }
        }
        for (User user : audience.users()) {
            if (user.getId().equals(actor.getId())) {
                continue;
            }
            UUID departmentId = user.getDepartment() != null ? user.getDepartment().getId() : null;
            boolean inDepartment = departmentId != null && allowedDepartments.contains(departmentId);
            boolean directReport = team && user.getManager() != null
                    && actor.getId().equals(user.getManager().getId());
            if (!inDepartment && !directReport) {
                violations.add("user '%s'".formatted(user.getFullName()));
            }
        }
        return violations;
    }

    /** Departments whose members, positions and whole membership the actor may invite. */
    public Set<UUID> allowedDepartmentIds(UserPrincipal actor) {
        Set<UUID> allowed = new HashSet<>();
        boolean ownDepartment = actor.hasAnyPermission(INVITE_DEPARTMENT, INVITE_ASSIGNED);
        if (ownDepartment && actor.getDepartmentId() != null) {
            allowed.add(actor.getDepartmentId());
        }
        if (actor.hasPermission(INVITE_ASSIGNED)) {
            allowed.addAll(scopeRepository.findDepartmentIdsByUserId(actor.getId()));
        }
        return allowed;
    }

    /** Organizer, or anyone with {@code bookings:manage:any}, may edit or cancel. */
    public boolean canManage(UserPrincipal actor, MeetingBooking booking) {
        return actor.hasPermission(MANAGE_ANY) || isOrganizer(actor, booking);
    }

    public boolean canView(UserPrincipal actor, MeetingBooking booking) {
        if (canManage(actor, booking) || actor.hasPermission(VIEW_ALL)) {
            return true;
        }
        return booking.getAttendees().stream().anyMatch(u -> Objects.equals(u.getId(), actor.getId()));
    }

    private static boolean isOrganizer(UserPrincipal actor, MeetingBooking booking) {
        return booking.getOrganizer() != null && actor.getId().equals(booking.getOrganizer().getId());
    }
}
