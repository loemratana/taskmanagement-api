package com.ratana.task_management_api.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ratana.task_management_api.booking.enums.MeetingLocationType;

import java.math.BigDecimal;

/**
 * Where a meeting is, for every location type. {@code label} is always set and is what to show
 * in lists; the other fields depend on {@code type}. mapUrl is a Google Maps link built from the pin.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingLocationResponse(
        MeetingLocationType type,
        String label,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String placeId,
        String mapUrl,
        String meetingUrl,
        String note
) {
}
