package com.ratana.task_management_api.task.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignMultipleUsersRequest {

    @NotEmpty(message = "At least one user is required")
    private List<UUID> userIds;
}
