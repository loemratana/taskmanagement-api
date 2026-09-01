package com.ratana.task_management_api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignMultipleUsersRequest {

    @NotEmpty(message = "At least one user id is required")
    private List<UUID> userIds;
}
