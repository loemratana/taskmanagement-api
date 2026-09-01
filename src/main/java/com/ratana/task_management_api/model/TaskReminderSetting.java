package com.ratana.task_management_api.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "task_reminder_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskReminderSetting extends BaseEnitty {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false, unique = true)
    private Task task;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(nullable = false, length = 64)
    @Builder.Default
    private String timezone = "Asia/Phnom_Penh";

    @OneToMany(mappedBy = "setting", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TaskReminderSchedule> schedules = new ArrayList<>();

    public void addSchedule(TaskReminderSchedule schedule) {
        schedules.add(schedule);
        schedule.setSetting(this);
    }
}
