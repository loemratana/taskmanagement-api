package com.ratana.task_management_api.booking.dto.request;

import com.ratana.task_management_api.booking.enums.RoomFacility;
import com.ratana.task_management_api.booking.enums.RoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomRequest {

    @NotBlank
    @Size(max = 100)
    @Schema(example = "Meeting Room A")
    private String name;

    @Size(max = 20)
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "code may contain only letters, digits, '-' and '_'")
    @Schema(example = "MR-3A")
    private String code;

    @Size(max = 200)
    @Schema(example = "Building B, Floor 3")
    private String location;

    @Size(max = 2000)
    private String description;

    @NotNull
    @Min(1)
    @Max(500)
    @Schema(description = "Maximum number of people, organizer included", example = "12")
    private Integer capacity;

    @Schema(description = "Defaults to ACTIVE")
    private RoomStatus status;

    @Builder.Default
    private Set<RoomFacility> facilities = new HashSet<>();
}
