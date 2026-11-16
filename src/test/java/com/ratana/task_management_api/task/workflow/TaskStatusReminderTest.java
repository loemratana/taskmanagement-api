package com.ratana.task_management_api.task.workflow;

import com.ratana.task_management_api.task.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskStatusReminderTest {

    @Test
    @DisplayName("no reminders once work is submitted, approved, done or cancelled")
    void finishedOrInReview_getNoReminders() {
        assertThat(TaskStatus.IN_REVIEW.needsReminders()).isFalse();
        assertThat(TaskStatus.APPROVED.needsReminders()).isFalse();
        assertThat(TaskStatus.DONE.needsReminders()).isFalse();
        assertThat(TaskStatus.CANCELLED.needsReminders()).isFalse();
    }

    @Test
    @DisplayName("reminders continue while the assignee still has work to do")
    void openWork_getsReminders() {
        assertThat(TaskStatus.TODO.needsReminders()).isTrue();
        assertThat(TaskStatus.IN_PROGRESS.needsReminders()).isTrue();
        assertThat(TaskStatus.REJECTED.needsReminders()).isTrue();
        assertThat(TaskStatus.BLOCKED.needsReminders()).isTrue();
    }
}
