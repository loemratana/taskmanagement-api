package com.ratana.task_management_api.dto.request;

import com.ratana.task_management_api.enums.Priority;
import com.ratana.task_management_api.enums.TaskStatus;
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
public class CreateTaskRequest {


    @NotBlank
    @Size(max = 500)
    private String title;
    private String description;
    @NotNull
    private Priority priority;

    /** Ignored on create — new tasks always start as {@link TaskStatus#TODO}. */
    private TaskStatus status;
    @NotNull
    private UUID departmentId;
    private UUID assigneeId;
    private UUID parentTaskId;
    @FutureOrPresent
    private LocalDate startDate;
    @FutureOrPresent
    private LocalDate dueDate;
    private BigDecimal estimatedHours;
    private Set<String> tags;

}
