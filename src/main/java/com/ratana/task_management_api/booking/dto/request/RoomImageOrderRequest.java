package com.ratana.task_management_api.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class RoomImageOrderRequest {

    @NotEmpty
    @Schema(description = "Every image id of the room, in the desired display order")
    private List<UUID> imageIds;
}
