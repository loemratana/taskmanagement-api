package com.ratana.task_management_api.booking.service;

import com.ratana.task_management_api.notification.dto.request.NotificationRequest;
import com.ratana.task_management_api.notification.enums.NotificationPriority;
import com.ratana.task_management_api.notification.enums.NotificationType;
import com.ratana.task_management_api.booking.event.MeetingBookingEvent;
import com.ratana.task_management_api.infrastructure.messaging.TelegramApiException;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.organization.entity.DepartmentTelegramGroup;
import com.ratana.task_management_api.booking.entity.MeetingBooking;
import com.ratana.task_management_api.booking.entity.MeetingLocation;
import com.ratana.task_management_api.organization.entity.Position;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.booking.repository.MeetingBookingRepository;
import com.ratana.task_management_api.organization.service.DepartmentTelegramGroupService;
import com.ratana.task_management_api.notification.service.NotificationService;
import com.ratana.task_management_api.infrastructure.messaging.TelegramBotClient;
import com.ratana.task_management_api.infrastructure.messaging.TelegramMessageFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Sends meeting invitations, changes and cancellations once the booking change is committed:
 * a personal notification (in-app / push / per user preferences) to every attendee reached through
 * the selected departments, positions and users, plus — on create and cancel — an announcement in the
 * Telegram group of each targeted department (and of the department each targeted position belongs to).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingBookingNotifier {

    private final MeetingBookingRepository bookingRepository;
    private final NotificationService notificationService;
    private final BookingTimePolicy timePolicy;
    private final DepartmentTelegramGroupService groupService;
    private final TelegramBotClient telegramBotClient;
    private final TelegramMessageFormatter messageFormatter;

    /** REQUIRES_NEW: NotificationService.send registers an after-commit hook that needs a live transaction. */
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(MeetingBookingEvent event) {
        MeetingBooking booking = bookingRepository.findWithDetailsById(event.bookingId()).orElse(null);
        if (booking == null) {
            log.info("Skipping meeting notifications: booking {} no longer exists", event.bookingId());
            return;
        }
        UUID senderId = event.actorId() != null ? event.actorId() : booking.getOrganizer().getId();
        String when = "%s %s-%s".formatted(timePolicy.localDate(booking.getStartAt()),
                timePolicy.localTime(booking.getStartAt()), timePolicy.localTime(booking.getEndAt()));
        String where = wherePhrase(booking);
        String organizer = booking.getOrganizer().getFullName();
        Map<String, Object> metadata = metadata(booking);

        int sent = 0;
        for (Map.Entry<NotificationType, Set<UUID>> entry : event.recipients().entrySet()) {
            NotificationType type = entry.getKey();
            String title = titleFor(type, booking.getTitle());
            String message = messageFor(type, booking, organizer, where, when);
            for (UUID recipientId : entry.getValue()) {
                try {
                    notificationService.send(NotificationRequest.builder()
                            .recipientId(recipientId)
                            .senderId(senderId)
                            .title(title)
                            .message(message)
                            .type(type)
                            .priority(type == NotificationType.MEETING_CANCELLED
                                    ? NotificationPriority.HIGH : NotificationPriority.MEDIUM)
                            .referenceType("MEETING_BOOKING")
                            .referenceId(booking.getId())
                            .metadata(metadata)
                            .build());
                    sent++;
                } catch (RuntimeException e) {
                    log.warn("Meeting {} notification ({}) to {} failed: {}",
                            booking.getId(), type, recipientId, e.getMessage());
                }
            }
        }
        log.info("Meeting {} sent {} notification(s)", booking.getId(), sent);

        if (event.groupAlert() != null) {
            alertDepartmentGroups(booking, event.groupAlert() == NotificationType.MEETING_CANCELLED, when);
        }
    }

    /** Best effort: one group failing never stops the others or the personal notifications. */
    private void alertDepartmentGroups(MeetingBooking booking, boolean cancelled, String when) {
        Map<UUID, Department> departments = new LinkedHashMap<>();
        booking.getTargetDepartments().forEach(d -> departments.put(d.getId(), d));
        booking.getTargetPositions().stream()
                .map(Position::getDepartment)
                .filter(Objects::nonNull)
                .forEach(d -> departments.putIfAbsent(d.getId(), d));
        if (departments.isEmpty()) {
            return;
        }

        String text = messageFormatter.forMeeting(booking, cancelled, when, audience(booking));
        int posted = 0;
        for (Department department : departments.values()) {
            DepartmentTelegramGroup group = groupService.resolveActive(department.getId()).orElse(null);
            if (group == null) {
                continue;
            }
            try {
                telegramBotClient.sendMessage(group.getChatId(), text);
                posted++;
            } catch (TelegramApiException e) {
                log.warn("Meeting {} Telegram alert to group {} (department {}) failed: {}",
                        booking.getId(), group.getId(), department.getId(), e.getMessage());
                if (!e.isRetryable()) {
                    // Bot kicked or chat deleted: stop routing to it, same as task alerts do.
                    groupService.deactivate(group.getId());
                }
            } catch (RuntimeException e) {
                log.warn("Meeting {} Telegram alert to group {} (department {}) failed: {}",
                        booking.getId(), group.getId(), department.getId(), e.getMessage());
            }
        }
        log.info("Meeting {} {} alert posted to {} department group(s)", booking.getId(),
                cancelled ? "cancellation" : "invitation", posted);
    }

    /** "IT, HR / Manager, Dara Sok" — what the organizer selected. */
    private static String audience(MeetingBooking booking) {
        List<String> parts = new ArrayList<>();
        booking.getTargetDepartments().forEach(d -> parts.add(d.getName()));
        booking.getTargetPositions().forEach(p -> parts.add(p.getDepartment() != null
                ? p.getDepartment().getName() + " / " + p.getName() : p.getName()));
        booking.getTargetUsers().stream().map(User::getFullName).forEach(parts::add);
        return parts.isEmpty() ? "Organizer only" : String.join(", ", parts);
    }

    /** Reads after "invited you to 'X' ...": "in Room A", "at ABC Café", "online (Zoom)". */
    static String wherePhrase(MeetingBooking booking) {
        return switch (booking.getLocationType()) {
            case ROOM -> "in " + booking.locationLabel();
            case EXTERNAL -> "at " + booking.locationLabel();
            case ONLINE -> booking.getLocation() != null && booking.getLocation().getName() != null
                    ? "online (" + booking.getLocation().getName() + ")" : "online";
        };
    }

    /** What apps need to open the meeting: the room, or the map pin / call link. */
    private static Map<String, Object> metadata(MeetingBooking booking) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("locationType", booking.getLocationType().name());
        if (booking.getRoom() != null) {
            metadata.put("roomId", booking.getRoom().getId().toString());
        }
        MeetingLocation location = booking.getLocation();
        if (location != null && location.mapUrl() != null) {
            metadata.put("mapUrl", location.mapUrl());
        }
        if (location != null && location.getMeetingUrl() != null) {
            metadata.put("meetingUrl", location.getMeetingUrl());
        }
        metadata.put("startAt", booking.getStartAt().toString());
        metadata.put("endAt", booking.getEndAt().toString());
        return metadata;
    }

    private static String titleFor(NotificationType type, String meetingTitle) {
        return switch (type) {
            case MEETING_INVITED -> "Meeting invitation: " + meetingTitle;
            case MEETING_CANCELLED -> "Meeting cancelled: " + meetingTitle;
            default -> "Meeting updated: " + meetingTitle;
        };
    }

    private static String messageFor(NotificationType type, MeetingBooking booking, String organizer,
                                     String where, String when) {
        return switch (type) {
            case MEETING_INVITED -> "%s invited you to '%s' %s on %s".formatted(
                    organizer, booking.getTitle(), where, when);
            case MEETING_CANCELLED -> booking.isConfirmed()
                    ? "You were removed from '%s' on %s".formatted(booking.getTitle(), when)
                    : "'%s' %s on %s was cancelled%s".formatted(booking.getTitle(), where, when,
                    booking.getCancelReason() != null ? ": " + booking.getCancelReason() : "");
            default -> "'%s' is now %s on %s".formatted(booking.getTitle(), where, when);
        };
    }
}
