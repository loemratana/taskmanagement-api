package com.ratana.task_management_api.booking.dto.response;

import java.util.List;
import java.util.UUID;

/** Departments a user was assigned for inviting (used with bookings:invite:assigned). */
public record BookingScopeResponse(UUID userId, List<BookingResponse.Ref> departments) {
}
