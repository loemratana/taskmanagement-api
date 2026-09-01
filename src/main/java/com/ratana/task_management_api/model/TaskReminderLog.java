package com.ratana.task_management_api.model;

import com.ratana.task_management_api.enums.ReminderLogStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row per (task, schedule slot, calendar date) that the scheduler has decided on.
 * The unique constraint is the actual duplicate-prevention mechanism — safe even across
 * concurrent scheduler runs or multiple app instances.
 */
@Entity
@Table(
        name = "task_reminder_logs",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"task_id", "task_reminder_schedule_id", "reminder_date"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskReminderLog extends BaseEnitty {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_reminder_schedule_id", nullable = false)
    private TaskReminderSchedule schedule;

    @Column(name = "reminder_date", nullable = false)
    private LocalDate reminderDate;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReminderLogStatus status;

    @Column(name = "notification_id")
    private UUID notificationId;
}
