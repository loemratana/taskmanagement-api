package com.ratana.task_management_api.dto.response;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Position details")
@Builder
@Data
public class PositionResponse {

    private UUID id;
    private String name;
    private String description;
    private UUID departmentId;
    private String departmentName;
    private Instant createdDate;
    private Instant updatedDate;



}
