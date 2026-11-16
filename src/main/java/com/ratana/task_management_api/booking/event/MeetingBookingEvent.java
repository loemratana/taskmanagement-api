package com.ratana.task_management_api.booking.event;

import com.ratana.task_management_api.notification.enums.NotificationType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Published when a booking is created, changed or cancelled; handled after commit.
 *
 * @param recipients who to tell what, e.g. MEETING_INVITED for newly added attendees
 *                   and MEETING_UPDATED for those already invited
 * @param groupAlert when set (MEETING_INVITED on create, MEETING_CANCELLED on cancel), the meeting is
 *                   also announced in the Telegram group of every targeted department and of the
 *                   department each targeted position belongs to; null for no group alert
 */
public record MeetingBookingEvent(UUID bookingId, UUID actorId, Map<NotificationType, Set<UUID>> recipients,
                                  NotificationType groupAlert) {
}
