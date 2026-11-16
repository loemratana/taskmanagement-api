package com.ratana.task_management_api.user.mapper;

import com.ratana.task_management_api.user.dto.request.UserCreateRequest;
import com.ratana.task_management_api.user.dto.request.UserUpdateRequest;
import com.ratana.task_management_api.organization.dto.response.DepartmentMemberResponse;
import com.ratana.task_management_api.common.response.Summary;
import com.ratana.task_management_api.user.dto.response.UserResponse;
import com.ratana.task_management_api.user.enums.UserStatus;
import com.ratana.task_management_api.user.entity.User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.stream.Collectors;

@Component
public class UserMapper {

    public  UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
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

                .roles(user.getRoles().stream()
                        .map(role -> new Summary(
                                role.getId(),
                                role.getName()
                        ))
                        .collect(Collectors.toSet()))

                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .active(user.isActive())
                .status(user.getStatus())
                .build();

    }

    public DepartmentMemberResponse toMemberResponse(User user) {
        return DepartmentMemberResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
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
        if (StringUtils.hasText(request.getPhoneNumber()))
        {
            user.setPhoneNumber(request.getPhoneNumber().trim());
        }
        user.setAvatarUrl(request.getAvatarUrl());
        user.setStatus(UserStatus.ACTIVE);
        user.setActive(true);
        return user;
    }

    public void upateEntity(User user , UserUpdateRequest request)
    {
        // Only fields present in the request are applied (partial update).
        if (StringUtils.hasText(request.getEmail()))
        {
            user.setEmail(request.getEmail().trim());
        }

        if (StringUtils.hasText(request.getFullName()))
        {
            user.setFullName(request.getFullName().trim());
        }
        if (StringUtils.hasText(request.getPhoneNumber()))
        {
            user.setPhoneNumber(request.getPhoneNumber().trim());
        }
        if (StringUtils.hasText(request.getAvartarUrl()))
        {
            user.setAvatarUrl(request.getAvartarUrl());
        }
    }
}
