package com.ratana.task_management_api.task.reminder;

import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;

import java.time.LocalDate;
import java.util.UUID;

public interface TaskReminderService {

    /** Read-only. Returns the persisted setting if one exists, else the computed system defaults — never writes. */
    ReminderSettingsResponse getSettings(UUID taskId, UserPrincipal actor);

    /** Upserts the task's reminder setting with the given partial changes. */
    ReminderSettingsResponse updateSettings(UUID taskId, ReminderSettingsUpdateRequest request, UserPrincipal actor);

    /** Loads the task's reminder setting, self-provisioning system defaults on first use. Used by the scheduler. */
    TaskReminderSetting getOrCreateSettings(Task task);

    /**
     * The per-candidate unit of work: claim-then-send with duplicate protection via the
     * DB unique constraint on (task, schedule, reminderDate). Safe to call repeatedly —
     * a slot already handled today is a no-op.
     */
    void processDueReminder(Task task, TaskReminderSchedule schedule, LocalDate reminderDate);
}
