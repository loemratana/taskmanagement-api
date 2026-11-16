package com.ratana.task_management_api.performance.service;

import com.ratana.task_management_api.performance.config.PerformanceProperties;
import com.ratana.task_management_api.performance.service.PerformanceScoreCalculator.MemberScore;
import com.ratana.task_management_api.performance.service.PerformanceScoreCalculator.TaskFacts;
import com.ratana.task_management_api.task.enums.Priority;
import com.ratana.task_management_api.task.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PerformanceScoreCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 30);
    private static final LocalDate CREATED = LocalDate.of(2026, 11, 1);
    private static final LocalDate DUE = LocalDate.of(2026, 11, 20);
    private static final LocalDate EARLY = LocalDate.of(2026, 11, 15);
    private static final LocalDate LATE = LocalDate.of(2026, 11, 22);

    static PerformanceProperties defaults(double lateWithReasonCredit) {
        return new PerformanceProperties(ZoneId.of("Asia/Phnom_Penh"), 30, 25, 20, 15, 10, 25,
                lateWithReasonCredit, true, 3, 5, 70, 0.75, 1, 1.5, 2);
    }

    private final PerformanceScoreCalculator calculator = new PerformanceScoreCalculator(defaults(0));

    private static TaskFacts task(TaskStatus status, LocalDate delivered, int rejections) {
        return new TaskFacts(Priority.MEDIUM, status, CREATED, null, DUE, delivered, false, rejections);
    }

    private static List<TaskFacts> times(int n, TaskFacts facts) {
        return new ArrayList<>(Collections.nCopies(n, facts));
    }

    @Test
    @DisplayName("Worked example: 20 solid tasks outrank 3 perfect ones, and both beat half-late work")
    void workedExample_volumeAndPunctualityWin() {
        List<TaskFacts> a = times(3, task(TaskStatus.DONE, EARLY, 0));

        List<TaskFacts> b = times(13, task(TaskStatus.DONE, EARLY, 0));
        b.addAll(times(3, task(TaskStatus.DONE, EARLY, 1)));
        b.addAll(times(2, task(TaskStatus.DONE, LATE, 0)));
        b.add(task(TaskStatus.IN_REVIEW, EARLY, 0));
        b.add(task(TaskStatus.TODO, null, 0));          // overdue, never delivered

        List<TaskFacts> c = times(5, task(TaskStatus.DONE, EARLY, 0));
        c.addAll(times(5, task(TaskStatus.DONE, LATE, 0)));

        MemberScore sa = calculator.score(a, TODAY);
        MemberScore sb = calculator.score(b, TODAY);
        MemberScore sc = calculator.score(c, TODAY);

        assertThat(sa.raw()).isCloseTo(96.3, within(0.1));
        assertThat(sb.raw()).isCloseTo(88.8, within(0.1));
        assertThat(sc.raw()).isCloseTo(78.2, within(0.1));

        Double fa = calculator.adjust(sa.raw(), sa.tasksCounted());
        Double fb = calculator.adjust(sb.raw(), sb.tasksCounted());
        Double fc = calculator.adjust(sc.raw(), sc.tasksCounted());
        assertThat(fb).isEqualTo(85.0);
        assertThat(fa).isEqualTo(79.9);
        assertThat(fc).isEqualTo(75.4);
        assertThat(calculator.stars(fb)).isEqualTo(4.5);
        assertThat(calculator.stars(fa)).isEqualTo(4.0);

        assertThat(sb.overdue()).isEqualTo(1);
        assertThat(sb.rejections()).isEqualTo(3);
        assertThat(sb.late()).isEqualTo(2);
        assertThat(sb.completionRate()).isEqualTo(90.0);
    }

    @Test
    @DisplayName("Each rejection costs quality points; four rejections bottom out at zero")
    void quality_dropsPerRejection() {
        assertThat(calculator.score(List.of(task(TaskStatus.DONE, EARLY, 1)), TODAY).quality()).isEqualTo(75.0);
        assertThat(calculator.score(List.of(task(TaskStatus.DONE, EARLY, 4)), TODAY).quality()).isEqualTo(0.0);
        assertThat(calculator.score(List.of(task(TaskStatus.DONE, EARLY, 6)), TODAY).quality()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Speed: late = 0, on the due date = 50, delivered at the start = 100")
    void speed_scale() {
        assertThat(calculator.score(List.of(task(TaskStatus.DONE, LATE, 0)), TODAY).speed()).isEqualTo(0.0);
        assertThat(calculator.score(List.of(task(TaskStatus.DONE, DUE, 0)), TODAY).speed()).isEqualTo(50.0);
        assertThat(calculator.score(List.of(task(TaskStatus.DONE, CREATED, 0)), TODAY).speed()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("A late reason earns the configured share of on-time credit")
    void lateWithReason_credit() {
        TaskFacts lateWithReason = new TaskFacts(Priority.MEDIUM, TaskStatus.DONE, CREATED, null, DUE, LATE, true, 0);
        assertThat(calculator.score(List.of(lateWithReason), TODAY).onTimeRate()).isEqualTo(0.0);

        PerformanceScoreCalculator halfCredit = new PerformanceScoreCalculator(defaults(0.5));
        assertThat(halfCredit.score(List.of(lateWithReason), TODAY).onTimeRate()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Urgent work weighs double a medium task")
    void priority_weighting() {
        TaskFacts urgentOnTime = new TaskFacts(Priority.URGENT, TaskStatus.DONE, CREATED, null, DUE, EARLY, false, 0);
        TaskFacts mediumLate = task(TaskStatus.DONE, LATE, 0);
        // 2 of 3 weight units on time.
        assertThat(calculator.score(List.of(urgentOnTime, mediumLate), TODAY).onTimeRate()).isEqualTo(66.7);
    }

    @Test
    @DisplayName("A task counts once: in its delivery period, or when its due date passed undelivered")
    void countsIn_periodRules() {
        LocalDate novFrom = LocalDate.of(2026, 11, 1);
        LocalDate novTo = LocalDate.of(2026, 11, 30);
        LocalDate octFrom = LocalDate.of(2026, 10, 1);
        LocalDate octTo = LocalDate.of(2026, 10, 31);

        assertThat(calculator.countsIn(task(TaskStatus.DONE, EARLY, 0), novFrom, novTo, TODAY)).isTrue();
        assertThat(calculator.countsIn(task(TaskStatus.DONE, EARLY, 0), octFrom, octTo, TODAY)).isFalse();
        assertThat(calculator.countsIn(task(TaskStatus.CANCELLED, EARLY, 0), novFrom, novTo, TODAY)).isFalse();
        // Undelivered: counts once its due date has passed, not before.
        assertThat(calculator.countsIn(task(TaskStatus.TODO, null, 0), novFrom, novTo, TODAY)).isTrue();
        assertThat(calculator.countsIn(task(TaskStatus.TODO, null, 0), novFrom, novTo, LocalDate.of(2026, 11, 10)))
                .isFalse();
    }

    @Test
    @DisplayName("Nothing counted: no score; fewer than 3 tasks: not rated")
    void noData_andRatingThreshold() {
        assertThat(calculator.score(List.of(), TODAY).raw()).isNull();
        assertThat(calculator.adjust(null, 0)).isNull();
        assertThat(calculator.isRated(2)).isFalse();
        assertThat(calculator.isRated(3)).isTrue();
    }
}
