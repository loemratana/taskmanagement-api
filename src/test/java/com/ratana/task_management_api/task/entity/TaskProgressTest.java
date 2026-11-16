package com.ratana.task_management_api.task.entity;

import com.ratana.task_management_api.task.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskProgressTest {

    @ParameterizedTest(name = "{0} -> {1}%")
    @CsvSource({
            "TODO, 0",
            "IN_PROGRESS, 25",
            "REJECTED, 25",
            "BLOCKED, 25",
            "IN_REVIEW, 50",
            "APPROVED, 100",
            "DONE, 100",
            "CANCELLED, 0"
    })
    @DisplayName("a task without subtasks shows its own status weight")
    void noSubtasks_usesStatusWeight(TaskStatus status, int expected) {
        assertThat(task(status).computeProgress()).isEqualTo(expected);
    }

    @Test
    @DisplayName("an open parent averages its subtasks' status weights")
    void openParent_averagesSubtasks() {
        Task parent = task(TaskStatus.IN_PROGRESS,
                task(TaskStatus.TODO),          // 0
                task(TaskStatus.IN_REVIEW),     // 50
                task(TaskStatus.DONE));         // 100
        assertThat(parent.computeProgress()).isEqualTo(50);
    }

    @Test
    @DisplayName("the average is rounded")
    void average_isRounded() {
        Task parent = task(TaskStatus.IN_PROGRESS,
                task(TaskStatus.IN_PROGRESS),   // 25
                task(TaskStatus.IN_PROGRESS),   // 25
                task(TaskStatus.IN_REVIEW));    // 50 -> 33.33
        assertThat(parent.computeProgress()).isEqualTo(33);
    }

    @Test
    @DisplayName("cancelled and inactive subtasks are left out of the average")
    void cancelledAndInactiveSubtasks_areSkipped() {
        Task inactive = task(TaskStatus.TODO);
        inactive.setActive(false);
        Task parent = task(TaskStatus.IN_PROGRESS,
                task(TaskStatus.DONE),
                task(TaskStatus.CANCELLED),
                inactive);
        assertThat(parent.computeProgress()).isEqualTo(100);
    }

    @Test
    @DisplayName("with only cancelled subtasks the parent falls back to its own status")
    void onlyCancelledSubtasks_fallsBackToStatus() {
        Task parent = task(TaskStatus.IN_REVIEW, task(TaskStatus.CANCELLED));
        assertThat(parent.computeProgress()).isEqualTo(50);
    }

    @Test
    @DisplayName("an approved or done parent is complete, and a cancelled one is 0, whatever its subtasks say")
    void finishedOrCancelledParent_ignoresSubtasks() {
        assertThat(task(TaskStatus.APPROVED, task(TaskStatus.TODO)).computeProgress()).isEqualTo(100);
        assertThat(task(TaskStatus.DONE, task(TaskStatus.TODO)).computeProgress()).isEqualTo(100);
        assertThat(task(TaskStatus.CANCELLED, task(TaskStatus.DONE)).computeProgress()).isEqualTo(0);
    }

    private static Task task(TaskStatus status, Task... subtasks) {
        return Task.builder()
                .status(status)
                .subtasks(new ArrayList<>(List.of(subtasks)))
                .build();
    }
}
