package com.ratana.task_management_api.booking.service;

import com.ratana.task_management_api.booking.dto.request.BookingLocationRequest;
import com.ratana.task_management_api.booking.entity.MeetingBooking;
import com.ratana.task_management_api.booking.entity.MeetingLocation;
import com.ratana.task_management_api.booking.entity.Room;
import com.ratana.task_management_api.booking.enums.MeetingLocationType;
import com.ratana.task_management_api.common.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingLocationPolicyTest {

    private final BookingLocationPolicy policy = new BookingLocationPolicy();

    private static BookingLocationRequest.BookingLocationRequestBuilder pin() {
        return BookingLocationRequest.builder()
                .name("  ABC Company HQ ")
                .address("St. 271, Phnom Penh")
                .latitude(new BigDecimal("11.5563738"))
                .longitude(new BigDecimal("104.9282099"))
                .placeId("ChIJ42tqxz1RCTERuyW1WugOAZw");
    }

    @Test
    @DisplayName("A missing type means ROOM, so existing clients keep working")
    void defaultsToRoom() {
        assertThat(policy.typeOrDefault(null)).isEqualTo(MeetingLocationType.ROOM);
        assertThat(policy.normalize(null, null)).isNull();
    }

    @Nested
    class Room_ {

        @Test
        @DisplayName("Keeps only the note; stale pin or link fields are dropped")
        void keepsOnlyNote() {
            MeetingLocation location = policy.normalize(MeetingLocationType.ROOM,
                    pin().meetingUrl("https://zoom.us/j/1").note(" Bring laptops ").build());

            assertThat(location.getNote()).isEqualTo("Bring laptops");
            assertThat(location.getName()).isNull();
            assertThat(location.getLatitude()).isNull();
            assertThat(location.getMeetingUrl()).isNull();
        }

        @Test
        void nothingToStoreWithoutNote() {
            assertThat(policy.normalize(MeetingLocationType.ROOM, pin().build())).isNull();
        }
    }

    @Nested
    class External {

        @Test
        @DisplayName("Stores the trimmed name, address, place id and 6-decimal coordinates")
        void storesPin() {
            MeetingLocation location = policy.normalize(MeetingLocationType.EXTERNAL,
                    pin().meetingUrl("https://zoom.us/j/1").build());

            assertThat(location.getName()).isEqualTo("ABC Company HQ");
            assertThat(location.getAddress()).isEqualTo("St. 271, Phnom Penh");
            assertThat(location.getLatitude()).isEqualByComparingTo("11.556374");
            assertThat(location.getLongitude()).isEqualByComparingTo("104.928210");
            assertThat(location.getGooglePlaceId()).isEqualTo("ChIJ42tqxz1RCTERuyW1WugOAZw");
            assertThat(location.getMeetingUrl()).as("link is not for EXTERNAL").isNull();
        }

        @Test
        void requiresName() {
            assertThatThrownBy(() -> policy.normalize(MeetingLocationType.EXTERNAL, pin().name(" ").build()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("name");
        }

        @Test
        void requiresPin() {
            assertThatThrownBy(() -> policy.normalize(MeetingLocationType.EXTERNAL, pin().longitude(null).build()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Pin the meeting place");
            assertThatThrownBy(() -> policy.normalize(MeetingLocationType.EXTERNAL, null))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void rejectsOutOfRangeCoordinates() {
            assertThatThrownBy(() -> policy.normalize(MeetingLocationType.EXTERNAL,
                    pin().latitude(new BigDecimal("91")).build()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Latitude");
        }
    }

    @Nested
    class Online {

        @Test
        @DisplayName("Stores the link and optional name; pin fields are dropped")
        void storesLink() {
            MeetingLocation location = policy.normalize(MeetingLocationType.ONLINE,
                    pin().name("Google Meet").meetingUrl(" https://meet.google.com/abc-defg-hij ").note("Camera on").build());

            assertThat(location.getMeetingUrl()).isEqualTo("https://meet.google.com/abc-defg-hij");
            assertThat(location.getName()).isEqualTo("Google Meet");
            assertThat(location.getNote()).isEqualTo("Camera on");
            assertThat(location.getLatitude()).isNull();
            assertThat(location.getAddress()).isNull();
            assertThat(location.getGooglePlaceId()).isNull();
        }

        @Test
        void requiresLink() {
            assertThatThrownBy(() -> policy.normalize(MeetingLocationType.ONLINE, BookingLocationRequest.builder().build()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("meeting link");
        }

        @ParameterizedTest
        @ValueSource(strings = {"meet.google.com/abc", "javascript:alert(1)", "ftp://files.example.com/x", "https://", "not a url"})
        void rejectsNonHttpLinks(String url) {
            assertThatThrownBy(() -> policy.normalize(MeetingLocationType.ONLINE,
                    BookingLocationRequest.builder().meetingUrl(url).build()))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    class Display {

        @Test
        @DisplayName("Map link uses the pin and the place id")
        void mapUrl() {
            MeetingLocation location = policy.normalize(MeetingLocationType.EXTERNAL, pin().build());

            assertThat(location.mapUrl()).isEqualTo("https://www.google.com/maps/search/?api=1"
                    + "&query=11.556374,104.928210&query_place_id=ChIJ42tqxz1RCTERuyW1WugOAZw");
            assertThat(new MeetingLocation().mapUrl()).isNull();
        }

        @Test
        void labelAndPhrasePerType() {
            Room room = new Room();
            room.setName("Room A");
            MeetingBooking inRoom = booking(MeetingLocationType.ROOM, null);
            inRoom.setRoom(room);
            MeetingBooking outside = booking(MeetingLocationType.EXTERNAL,
                    policy.normalize(MeetingLocationType.EXTERNAL, pin().build()));
            MeetingBooking zoom = booking(MeetingLocationType.ONLINE, policy.normalize(MeetingLocationType.ONLINE,
                    BookingLocationRequest.builder().name("Zoom").meetingUrl("https://zoom.us/j/1").build()));
            MeetingBooking call = booking(MeetingLocationType.ONLINE, policy.normalize(MeetingLocationType.ONLINE,
                    BookingLocationRequest.builder().meetingUrl("https://zoom.us/j/1").build()));

            assertThat(inRoom.locationLabel()).isEqualTo("Room A");
            assertThat(outside.locationLabel()).isEqualTo("ABC Company HQ");
            assertThat(call.locationLabel()).isEqualTo("Online meeting");

            assertThat(MeetingBookingNotifier.wherePhrase(inRoom)).isEqualTo("in Room A");
            assertThat(MeetingBookingNotifier.wherePhrase(outside)).isEqualTo("at ABC Company HQ");
            assertThat(MeetingBookingNotifier.wherePhrase(zoom)).isEqualTo("online (Zoom)");
            assertThat(MeetingBookingNotifier.wherePhrase(call)).isEqualTo("online");
        }

        private MeetingBooking booking(MeetingLocationType type, MeetingLocation location) {
            MeetingBooking booking = new MeetingBooking();
            booking.setLocationType(type);
            booking.setLocation(location);
            return booking;
        }
    }
}
