package com.ratana.task_management_api.booking.service;

import com.ratana.task_management_api.booking.exception.BookingScopeException;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.organization.entity.Position;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.booking.repository.UserBookingScopeRepository;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.booking.service.BookingAudienceResolver.ResolvedAudience;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingAuthorizationServiceTest {

    @Mock
    private UserBookingScopeRepository scopeRepository;

    private BookingAuthorizationService service;

    private final Department it = department("IT");
    private final Department hr = department("HR");
    private final Department finance = department("Finance");
    private final UUID managerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BookingAuthorizationService(scopeRepository);
    }

    @Test
    void departmentScopeAllowsOwnDepartmentOnly() {
        UserPrincipal manager = principal(it, "bookings:invite:department");

        assertThat(service.scopeViolations(manager, audience(Set.of(it), Set.of(position("Dev", it)),
                Set.of(user("Dara", it, null))))).isEmpty();

        assertThatThrownBy(() -> service.checkCanInvite(manager, audience(Set.of(it, hr), Set.of(), Set.of())))
                .isInstanceOf(BookingScopeException.class)
                .hasMessageContaining("department 'HR'")
                .hasMessageNotContaining("'IT'");
    }

    @Test
    void assignedScopeAddsAdminGrantedDepartments() {
        UserPrincipal manager = principal(it, "bookings:invite:assigned");
        when(scopeRepository.findDepartmentIdsByUserId(managerId)).thenReturn(Set.of(hr.getId()));

        assertThat(service.scopeViolations(manager, audience(Set.of(it, hr), Set.of(position("Recruiter", hr)),
                Set.of()))).isEmpty();
        assertThat(service.scopeViolations(manager, audience(Set.of(finance), Set.of(), Set.of())))
                .containsExactly("department 'Finance'");
    }

    @Test
    void teamScopeAllowsDirectReportsInOtherDepartments() {
        UserPrincipal manager = principal(it, "bookings:invite:team");
        User me = new User();
        me.setId(managerId);

        User report = user("Sokha", finance, me);
        User stranger = user("Vanna", finance, null);

        assertThat(service.scopeViolations(manager, audience(Set.of(), Set.of(), Set.of(report)))).isEmpty();
        assertThat(service.scopeViolations(manager, audience(Set.of(), Set.of(), Set.of(stranger))))
                .containsExactly("user 'Vanna'");
        // Team scope does not cover whole departments, not even the manager's own.
        assertThat(service.scopeViolations(manager, audience(Set.of(it), Set.of(), Set.of())))
                .containsExactly("department 'IT'");
    }

    @Test
    void allScopeAllowsAnything() {
        UserPrincipal admin = principal(it, "bookings:invite:all");
        assertThat(service.scopeViolations(admin, audience(Set.of(hr, finance), Set.of(position("CFO", finance)),
                Set.of(user("X", hr, null))))).isEmpty();
    }

    @Test
    void noInvitePermissionAllowsOnlyBookingForYourself() {
        UserPrincipal employee = principal(it);
        assertThat(service.scopeViolations(employee, audience(Set.of(), Set.of(), Set.of()))).isEmpty();
        User self = user("Me", it, null);
        self.setId(managerId);
        assertThat(service.scopeViolations(employee, audience(Set.of(), Set.of(), Set.of(self)))).isEmpty();
        assertThat(service.scopeViolations(employee, audience(Set.of(), Set.of(), Set.of(user("Colleague", it, null)))))
                .containsExactly("user 'Colleague'");
    }

    // ---------------------------------------------------------------- fixtures

    private UserPrincipal principal(Department department, String... permissions) {
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        Arrays.stream(permissions).map(SimpleGrantedAuthority::new).forEach(authorities::add);
        return new UserPrincipal(managerId, "m@example.com", "x", department.getId(), authorities, true);
    }

    private static ResolvedAudience audience(Set<Department> departments, Set<Position> positions, Set<User> users) {
        return new ResolvedAudience(departments, positions, users, Set.of());
    }

    private static Department department(String name) {
        Department department = new Department();
        department.setId(UUID.randomUUID());
        department.setName(name);
        return department;
    }

    private static Position position(String name, Department department) {
        Position position = new Position();
        position.setId(UUID.randomUUID());
        position.setName(name);
        position.setDepartment(department);
        return position;
    }

    private static User user(String name, Department department, User manager) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName(name);
        user.setDepartment(department);
        user.setManager(manager);
        return user;
    }
}
