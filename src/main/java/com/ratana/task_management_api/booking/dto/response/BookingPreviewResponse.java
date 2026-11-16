package com.ratana.task_management_api.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.util.List;

/** What would happen if the booking were saved; nothing is stored. */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingPreviewResponse(
        /* Unique attendees, organizer included. */
        int attendeeCount,
        /* Null when no room was given. */
        Integer roomCapacity,
        /* Null when no room was given. */
        Boolean fitsRoom,
        /* Null when no room/time was given. */
        Boolean roomAvailable,
        /* Attendees already in another meeting at that time; null without a time. */
        Long attendeesWithConflicts,
        /* Targets outside the organizer's invite scope; saving would be rejected. */
        List<String> scopeViolations,
        /* Free, active rooms big enough for everyone at that time; null without a time. */
        List<RoomSummaryResponse> suggestedRooms
) {
}
