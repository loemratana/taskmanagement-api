package com.ratana.task_management_api.booking.dto.request;

import com.ratana.task_management_api.booking.enums.RoomFacility;
import com.ratana.task_management_api.booking.enums.RoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** All filters optional, combined with AND. */
@Data
public class RoomFilterRequest {

    @Schema(description = "Matches name, code or location")
    private String search;

    private Integer minCapacity;

    @Schema(description = "Room must have this facility")
    private RoomFacility facility;

    private RoomStatus status;
}
