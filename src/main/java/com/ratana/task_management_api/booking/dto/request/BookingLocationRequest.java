package com.ratana.task_management_api.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Where the meeting is when it is not in one of our rooms. Fields that don't apply to the
 * chosen location type are ignored:
 * <ul>
 *   <li>EXTERNAL: name, latitude and longitude required (from the map pin); address and placeId optional.</li>
 *   <li>ONLINE: meetingUrl required; name optional (e.g. "Zoom").</li>
 *   <li>ROOM: only note is used.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingLocationRequest {

    @Size(max = 200)
    @Schema(example = "ABC Company HQ")
    private String name;

    @Size(max = 500)
    @Schema(example = "St. 271, Phnom Penh")
    private String address;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    @Schema(example = "11.556374")
    private BigDecimal latitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    @Schema(example = "104.928207")
    private BigDecimal longitude;

    @Size(max = 255)
    @Schema(description = "Google Maps place id from the place picker, if any", example = "ChIJ42tqxz1RCTERuyW1WugOAZw")
    private String placeId;

    @Size(max = 1000)
    @Schema(example = "https://meet.google.com/abc-defg-hij")
    private String meetingUrl;

    @Size(max = 500)
    @Schema(example = "3rd floor, ask reception for the IT team")
    private String note;
}
