package com.ratana.task_management_api.booking.service;

import com.ratana.task_management_api.booking.dto.request.BookingLocationRequest;
import com.ratana.task_management_api.booking.entity.MeetingLocation;
import com.ratana.task_management_api.booking.enums.MeetingLocationType;
import com.ratana.task_management_api.common.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Validates and cleans what the organizer sent about where the meeting is. Fields that don't
 * apply to the chosen type are dropped, so a frontend switching from EXTERNAL to ONLINE can
 * leave stale pin data in the request without breaking the save.
 */
@Component
public class BookingLocationPolicy {

    private static final int COORDINATE_SCALE = 6;
    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

    /** Requests written before location types existed are room bookings. */
    public MeetingLocationType typeOrDefault(MeetingLocationType type) {
        return type != null ? type : MeetingLocationType.ROOM;
    }

    /**
     * @return the details to store, or null when there is nothing beyond the room itself
     * @throws BadRequestException when a required field for the type is missing or invalid
     */
    public MeetingLocation normalize(MeetingLocationType type, BookingLocationRequest request) {
        BookingLocationRequest in = request != null ? request : new BookingLocationRequest();
        String note = clean(in.getNote());
        return switch (typeOrDefault(type)) {
            case ROOM -> note == null ? null : MeetingLocation.builder().note(note).build();
            case EXTERNAL -> external(in, note);
            case ONLINE -> online(in, note);
        };
    }

    private MeetingLocation external(BookingLocationRequest in, String note) {
        String name = clean(in.getName());
        if (name == null) {
            throw new BadRequestException("Give the place a name for a meeting outside our rooms");
        }
        if (in.getLatitude() == null || in.getLongitude() == null) {
            throw new BadRequestException("Pin the meeting place on the map (latitude and longitude are required)");
        }
        return MeetingLocation.builder()
                .name(name)
                .address(clean(in.getAddress()))
                .latitude(coordinate(in.getLatitude(), MAX_LATITUDE, "Latitude"))
                .longitude(coordinate(in.getLongitude(), MAX_LONGITUDE, "Longitude"))
                .googlePlaceId(clean(in.getPlaceId()))
                .note(note)
                .build();
    }

    private MeetingLocation online(BookingLocationRequest in, String note) {
        String url = clean(in.getMeetingUrl());
        if (url == null) {
            throw new BadRequestException("Add the meeting link for an online meeting");
        }
        return MeetingLocation.builder()
                .name(clean(in.getName()))
                .meetingUrl(validUrl(url))
                .note(note)
                .build();
    }

    private static BigDecimal coordinate(BigDecimal value, BigDecimal max, String label) {
        if (value.abs().compareTo(max) > 0) {
            throw new BadRequestException("%s must be between -%s and %s".formatted(label, max, max));
        }
        return value.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
    }

    /** Only absolute http(s) links: they end up as clickable links in apps, email and Telegram. */
    private static String validUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((scheme.equals("https") || scheme.equals("http")) && StringUtils.hasText(uri.getHost())) {
                return url;
            }
        } catch (URISyntaxException ignored) {
            // fall through to the error below
        }
        throw new BadRequestException("The meeting link must be a full http(s) URL, e.g. https://meet.google.com/abc-defg-hij");
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
