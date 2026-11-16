package com.ratana.task_management_api.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Where a meeting happens when it is not (only) a room. Which fields are set depends on
 * {@link com.ratana.task_management_api.booking.enums.MeetingLocationType}:
 * EXTERNAL uses name/address/coordinates/placeId, ONLINE uses meetingUrl (name optional),
 * and note applies to every type. The meeting_bookings_location_check constraint enforces this.
 */
@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingLocation {

    private static final String MAPS_SEARCH_URL = "https://www.google.com/maps/search/?api=1&query=";

    @Column(name = "location_name", length = 200)
    private String name;

    @Column(name = "location_address", length = 500)
    private String address;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    /** Google Maps place id from the frontend's place picker; makes the map link open the exact place. */
    @Column(name = "google_place_id", length = 255)
    private String googlePlaceId;

    @Column(name = "meeting_url", length = 1000)
    private String meetingUrl;

    /** Free text for attendees: "3rd floor, ask reception", "Passcode 1234", ... */
    @Column(name = "location_note", length = 500)
    private String note;

    /** A Google Maps link built from the stored pin, or null when there are no coordinates. */
    public String mapUrl() {
        if (latitude == null || longitude == null) {
            return null;
        }
        String url = MAPS_SEARCH_URL + latitude.toPlainString() + "," + longitude.toPlainString();
        if (googlePlaceId != null) {
            url += "&query_place_id=" + URLEncoder.encode(googlePlaceId, StandardCharsets.UTF_8);
        }
        return url;
    }
}
