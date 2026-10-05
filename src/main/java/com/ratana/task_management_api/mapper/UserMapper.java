package com.ratana.task_management_api.mapper;

import com.ratana.task_management_api.dto.request.UserCreateRequest;
import com.ratana.task_management_api.dto.request.UserUpdateRequest;
import com.ratana.task_management_api.dto.response.DepartmentMemberResponse;
import com.ratana.task_management_api.dto.response.ManagerResponse;
import com.ratana.task_management_api.dto.response.Summary;
import com.ratana.task_management_api.dto.response.UserResponse;
import com.ratana.task_management_api.enums.UserStatus;
import com.ratana.task_management_api.model.User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class UserMapper {

    public  UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())

                .department(user.getDepartment() == null
                        ? null
                        : new Summary(
                        user.getDepartment().getId(),
                        user.getDepartment().getName()
                ))

                .position(user.getPosition() == null
                        ? null
                        : new Summary(
                        user.getPosition().getId(),
                        user.getPosition().getName()
                ))

                .manager(user.getManager() == null
                        ? null
                        : new Summary(
                        user.getManager().getId(),
                        user.getManager().getFullName()
                ))

                .role(user.getRole() == null
                        ? null
                        : new Summary(
                        user.getRole().getId(),
                        user.getRole().getName()
                ))

                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .active(user.isActive())
                .status(user.getStatus())
                .build();

    }

    public ManagerResponse toManagerResponse(User manager) {
        return ManagerResponse.builder()
                .id(manager.getId())
                .name(manager.getFullName())
                .email(manager.getEmail())
                .profile(manager.getAvatarUrl())
                .build();
    }

    public DepartmentMemberResponse toMemberResponse(User user) {
        return DepartmentMemberResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .position(user.getPosition() == null
                        ? null
                        : new Summary(
                        user.getPosition().getId(),
                        user.getPosition().getName()
                ))
                .build();
    }

    public User toEntity(UserCreateRequest request)
    {
        User user = new User();
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setAvatarUrl(request.getAvatarUrl());
        user.setStatus(UserStatus.ACTIVE);
        user.setActive(true);
        return user;
    }

    public void upateEntity(User user , UserUpdateRequest request)
    {
        if (StringUtils.hasText(user.getEmail()))
        {
            user.setEmail(request.getEmail());
        }

        if (StringUtils.hasText(user.getFullName()))
        {
            user.setFullName(request.getFullName());
        }
        if (StringUtils.hasText(request.getAvartarUrl()))
        {
            user.setAvatarUrl(request.getAvartarUrl());
        }
    }
}
