package com.ratana.task_management_api.dto.request;


import com.ratana.task_management_api.model.Permission;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoleRequest {
    @Schema(example = "HR_MANAGER")
    @NotBlank
    @Size(max = 100)
    private String name;

    @Schema(example = "Manages employee records and department structure")
    private  String description;



    @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private Set<UUID> permissionIds;
}
