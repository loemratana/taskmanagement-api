package com.ratana.task_management_api.booking.service;

import com.ratana.task_management_api.booking.dto.response.BookingScopeResponse;
import com.ratana.task_management_api.common.exception.BadRequestException;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.booking.mapper.RoomBookingMapper;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.booking.entity.UserBookingScope;
import com.ratana.task_management_api.organization.repository.DepartmentRepository;
import com.ratana.task_management_api.booking.repository.UserBookingScopeRepository;
import com.ratana.task_management_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Admin-managed ABAC attribute: which departments a user may invite (with bookings:invite:assigned). */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingScopeService {

    private final UserBookingScopeRepository scopeRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final RoomBookingMapper mapper;

    @Transactional(readOnly = true)
    public BookingScopeResponse get(UUID userId) {
        requireUser(userId);
        return toResponse(userId, scopeRepository.findByUserIdWithDepartment(userId));
    }

    @Transactional
    public BookingScopeResponse replace(UUID userId, Set<UUID> departmentIds, UUID grantedById) {
        User user = requireUser(userId);
        List<Department> departments = departmentIds.isEmpty() ? List.of()
                : departmentRepository.findAllById(departmentIds);
        if (departments.size() != departmentIds.size()) {
            throw new BadRequestException("One or more departments were not found");
        }

        scopeRepository.deleteByUserId(userId);
        scopeRepository.flush();
        User grantedBy = userRepository.getReferenceById(grantedById);
        List<UserBookingScope> scopes = departments.stream().map(department -> {
            UserBookingScope scope = new UserBookingScope();
            scope.setUser(user);
            scope.setDepartment(department);
            scope.setGrantedBy(grantedBy);
            return scope;
        }).toList();
        scopeRepository.saveAll(scopes);

        log.info("Booking scope of user {} set to {} department(s) by {}", userId, scopes.size(), grantedById);
        return toResponse(userId, scopes);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private BookingScopeResponse toResponse(UUID userId, List<UserBookingScope> scopes) {
        return new BookingScopeResponse(userId, scopes.stream()
                .map(scope -> mapper.toRef(scope.getDepartment()))
                .toList());
    }
}
