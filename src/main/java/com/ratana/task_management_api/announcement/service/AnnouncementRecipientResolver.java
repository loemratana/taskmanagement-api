package com.ratana.task_management_api.announcement.service;

import com.ratana.task_management_api.announcement.entity.Announcement;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Turns an announcement's audience into the ids of active users who should receive it. */
@Component
@RequiredArgsConstructor
public class AnnouncementRecipientResolver {

    private final UserRepository userRepository;

    public Set<UUID> resolve(Announcement announcement) {
        List<UUID> ids = switch (announcement.getAudienceType()) {
            case EVERYONE -> userRepository.findActiveUserIds();
            case DEPARTMENT -> idsOrEmpty(announcement.getTargetDepartments(), Department::getId,
                    userRepository::findActiveUserIdsByDepartmentIds);
            case TEAM -> idsOrEmpty(announcement.getTargetTeamManagers(), User::getId,
                    userRepository::findActiveUserIdsByManagerIds);
            case SELECTED_EMPLOYEES -> idsOrEmpty(announcement.getTargetUsers(), User::getId,
                    userRepository::findActiveUserIdsByIdIn);
        };
        return new LinkedHashSet<>(ids);
    }

    private <T> List<UUID> idsOrEmpty(Collection<T> targets, Function<T, UUID> idOf,
                                      Function<Collection<UUID>, List<UUID>> query) {
        if (targets == null || targets.isEmpty()) {
            return List.of(); // empty IN () is invalid SQL
        }
        return query.apply(targets.stream().map(idOf).collect(Collectors.toSet()));
    }
}
