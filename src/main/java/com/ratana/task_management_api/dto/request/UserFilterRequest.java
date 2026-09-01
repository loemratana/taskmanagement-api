package com.ratana.task_management_api.dto.request;

import com.ratana.task_management_api.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserFilterRequest {

    private String keyword;
    private UUID departmentId;
    private UUID positionId;
    private UUID managerId;
    private UUID roleId;
    private UserStatus userStatus;
    private Boolean active;
}
