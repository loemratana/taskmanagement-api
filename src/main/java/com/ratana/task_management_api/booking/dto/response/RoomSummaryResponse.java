package com.ratana.task_management_api.booking.dto.response;

import java.util.UUID;

/** Compact room for booking responses and room suggestions. */
public record RoomSummaryResponse(UUID id, String name, String code, String location,
                                  int capacity, String coverImageUrl) {
}
