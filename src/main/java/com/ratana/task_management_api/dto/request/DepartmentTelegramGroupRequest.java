package com.ratana.task_management_api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Schema(description = "Payload to create or update a department's Telegram group mapping")
@AllArgsConstructor
@NoArgsConstructor
public class DepartmentTelegramGroupRequest {

    @Schema(
            description = "Department this Telegram group belongs to",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    )
    @NotNull
    private UUID departmentId;

    @Schema(description = "Telegram group chat id (bot must already be a member)", example = "-1001234567890")
    @NotBlank
    @Size(max = 64)
    private String chatId;

    @Schema(description = "Display name for the group, for admin reference only", example = "IT Department")
    @Size(max = 255)
    private String groupName;
}
