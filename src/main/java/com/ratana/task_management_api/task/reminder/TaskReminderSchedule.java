package com.ratana.task_management_api.task.reminder;

import com.ratana.task_management_api.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Table(
        name = "task_reminder_schedules",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"task_reminder_setting_id", "reminder_time"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskReminderSchedule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_reminder_setting_id", nullable = false)
    private TaskReminderSetting setting;

    @Column(name = "reminder_time", nullable = false)
    private LocalTime reminderTime;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
