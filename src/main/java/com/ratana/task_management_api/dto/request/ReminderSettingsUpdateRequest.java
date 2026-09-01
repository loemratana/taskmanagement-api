package com.ratana.task_management_api.dto.request;

import lombok.*;

import java.time.LocalTime;
import java.util.List;

/**
 * Partial update — a null field is left unchanged. To clear/replace the reminder
 * times, pass the full desired list; it is not merged item-by-item.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderSettingsUpdateRequest {

    private Boolean enabled;
    private String timezone;
    private List<LocalTime> reminderTimes;
}
