package com.ratana.task_management_api.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Who to invite. All three lists are combined (union) and de-duplicated; the organizer
 * is always an attendee. All empty = the organizer alone.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingTargetsRequest {

    @Schema(description = "Every active member of these departments")
    @Size(max = 50)
    @Builder.Default
    private Set<UUID> departmentIds = new HashSet<>();

    @Schema(description = "Every active user holding these positions")
    @Size(max = 100)
    @Builder.Default
    private Set<UUID> positionIds = new HashSet<>();

    @Schema(description = "Individually selected users")
    @Size(max = 500)
    @Builder.Default
    private Set<UUID> userIds = new HashSet<>();

    public static BookingTargetsRequest empty() {
        return new BookingTargetsRequest();
    }
}
