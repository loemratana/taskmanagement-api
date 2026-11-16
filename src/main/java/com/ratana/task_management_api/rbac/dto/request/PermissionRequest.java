package com.ratana.task_management_api.rbac.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Payload to create or update a permission")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PermissionRequest {


    @Schema(example = "department:create")
    @NotBlank
    @Size(max = 100)
    private String name;



    @Schema(example = "create")
    @NotBlank @Size(max = 50)
    private String module;


    @Schema(example = "create")
    @NotBlank @Size(max = 50)
    private String action;
    @Schema(example = "Allows creating new departments")
    private String description;
}
