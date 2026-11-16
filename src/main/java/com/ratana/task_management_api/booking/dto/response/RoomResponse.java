package com.ratana.task_management_api.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ratana.task_management_api.booking.enums.RoomFacility;
import com.ratana.task_management_api.booking.enums.RoomStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoomResponse(
        UUID id,
        String name,
        String code,
        String location,
        String description,
        int capacity,
        RoomStatus status,
        Set<RoomFacility> facilities,
        String coverImageUrl,
        List<RoomImageResponse> images,
        Instant createdAt,
        Instant updatedAt,
        /* Set after an update that affects future bookings, e.g. capacity lowered below a booking's size. */
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> warnings
) {

    public record RoomImageResponse(UUID id, String url, String contentType, long sizeBytes,
                                    int sortOrder, boolean cover) {
    }

    public RoomResponse withWarnings(List<String> newWarnings) {
        return new RoomResponse(id, name, code, location, description, capacity, status, facilities,
                coverImageUrl, images, createdAt, updatedAt, newWarnings);
    }
}
