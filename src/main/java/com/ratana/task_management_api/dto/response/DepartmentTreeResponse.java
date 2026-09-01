package com.ratana.task_management_api.dto.response;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Schema(description = "Department node within the tree hierarchy")
@Builder
@Data
public class DepartmentTreeResponse {

    private UUID id;
    private String name;
    private UUID managerId;
    boolean active;
    List<DepartmentTreeResponse> children;

}
