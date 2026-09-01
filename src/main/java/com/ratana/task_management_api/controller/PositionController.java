package com.ratana.task_management_api.controller;


import com.ratana.task_management_api.dto.request.PositionRequest;
import com.ratana.task_management_api.dto.response.ApiResponse;
import com.ratana.task_management_api.dto.response.ErrorResponse;
import com.ratana.task_management_api.dto.response.PositionResponse;
import com.ratana.task_management_api.service.PositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Positions", description = "Job position management")
@RestController
@RequestMapping("/api/v1/positions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PositionController {

    private final PositionService positionService;

    @Operation(summary = "List all positions")
    @GetMapping
    @PreAuthorize("hasAuthority('position:read')")
    public ResponseEntity<ApiResponse<List<PositionResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(positionService.getAll()));
    }

    @Operation(summary = "Get position details")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Position found",
                    content = @Content(schema = @Schema(implementation = PositionResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Position not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PositionResponse>> getById(
            @Parameter(description = "Position UUID") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(positionService.getById(id)));
    }


    @Operation(summary = "Create a position", description = "Admin only.")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Position created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Department not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Position code already exists")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<PositionResponse>> create(
            @Valid @RequestBody PositionRequest request) {
        PositionResponse created = positionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Position created", created));
    }


    @Operation(summary = "Update a position", description = "Admin only.")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Position updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Position or department not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Position code already exists")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PositionResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody PositionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Position updated", positionService.update(id, request)));
    }

    @Operation(summary = "Delete a position", description = "Admin only. Soft-deletes the position (deactivates it).")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Position deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Position not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        positionService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Position deleted"));
    }
}