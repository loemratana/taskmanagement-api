package com.ratana.task_management_api.dto.request;

import com.ratana.task_management_api.enums.Priority;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkCreateTaskRequest {

    @NotBlank
    @Size(max = 500)
    private String title;
    private String description;
    @NotNull
    private Priority priority;

    /** Target department — every active member gets a copy of the task. */
    @NotNull
    private UUID departmentId;
    /** Optional — narrows the targets to one position inside the department. */
    private UUID positionId;

    @FutureOrPresent
    private LocalDate startDate;
    @FutureOrPresent
    private LocalDate dueDate;
    private BigDecimal estimatedHours;
    private Set<String> tags;
}
