package com.ratana.task_management_api.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
public class BookingScopeRequest {

    @NotNull
    @Schema(description = "Replaces the user's assigned departments; empty removes them all")
    private Set<UUID> departmentIds = new HashSet<>();
}
