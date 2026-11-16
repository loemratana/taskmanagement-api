package com.ratana.task_management_api.task.dto.request;

import com.ratana.task_management_api.task.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTaskRequest {

    @NotBlank
    @Size(max = 500)
    private String title;
    private String description;
    @NotNull
    private Priority priority;
    private LocalDate dueDate;
}
