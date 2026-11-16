package com.ratana.task_management_api.task.reminder;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReminderSettingsResponse {

    private UUID taskId;
    private boolean enabled;
    private String timezone;

    @JsonFormat(pattern = "HH:mm")
    private List<LocalTime> reminderTimes;
}
