package com.ratana.task_management_api.dto.response;

import lombok.Builder;

import java.util.UUID;
@Builder
public record ManagerResponse(
        UUID id,
        String name,
        String email,
        String profile

) {
}
