package com.ratana.task_management_api.performance.service;

import com.ratana.task_management_api.performance.config.PerformanceProperties;
import com.ratana.task_management_api.task.enums.Priority;
import com.ratana.task_management_api.task.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Pure scoring rules, no database access. One person's tasks in a period become five 0-100
 * components, a weighted raw score, a fairness-adjusted score and a star rating.
 *
 * <p>Each task counts once: in the period its work was first delivered (first submission, or
 * completion for tasks finished without one), or, if never delivered, in the period its due date
 * passed. Cancelled tasks and tasks not yet due never count.
 */
@Component
@RequiredArgsConstructor
public class PerformanceScoreCalculator {

    /** Statuses where the assignee still owes work; past the due date these are overdue. */
    private static final Set<TaskStatus> OWES_WORK =
            EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.REJECTED, TaskStatus.BLOCKED);

    private final PerformanceProperties properties;

    /**
     * What the score needs to know about one task.
     *
     * @param deliveredDate      date of the first submission, else the completion date, else null
     * @param lateWithReason     the first submission was late and gave a reason
     * @param rejections         rejected submissions on the task
     */
    public record TaskFacts(Priority priority, TaskStatus status, LocalDate createdDate, LocalDate startDate,
                            LocalDate dueDate, LocalDate deliveredDate, boolean lateWithReason, int rejections) {

        public boolean completed() {
            return status == TaskStatus.APPROVED || status == TaskStatus.DONE;
        }
    }

    /** Component scores are 0-100 and null when there is nothing to measure them on. */
    public record MemberScore(int tasksCounted, int completed, int onTime, int late, int rejections, int overdue,
                              double weightedCompleted, Double onTimeRate, Double quality, Double completionRate,
                              Double overdueScore, Double speed, Double raw) {
    }

    /** Whether a task belongs to the period [from, to]. */
    public boolean countsIn(TaskFacts task, LocalDate from, LocalDate to, LocalDate today) {
        if (task.status() == TaskStatus.CANCELLED) {
            return false;
        }
        if (task.deliveredDate() != null) {
            return within(task.deliveredDate(), from, to);
        }
        return task.dueDate() != null && task.dueDate().isBefore(today) && within(task.dueDate(), from, to);
    }

    public MemberScore score(List<TaskFacts> tasks, LocalDate today) {
        double totalWeight = 0;
        double completedWeight = 0;
        double overdueWeight = 0;
        double deliveredWeight = 0;
        double onTimeCredit = 0;
        double qualityPoints = 0;
        double speedWeight = 0;
        double speedPoints = 0;
        int completed = 0;
        int onTime = 0;
        int late = 0;
        int rejections = 0;
        int overdue = 0;

        for (TaskFacts task : tasks) {
            double weight = properties.priorityWeight(task.priority());
            totalWeight += weight;
            rejections += task.rejections();
            if (task.completed()) {
                completedWeight += weight;
                completed++;
            }
            if (isOverdue(task, today)) {
                overdueWeight += weight;
                overdue++;
            }
            LocalDate delivered = task.deliveredDate();
            if (delivered == null) {
                continue;
            }
            deliveredWeight += weight;
            qualityPoints += weight * Math.max(0, 100 - properties.rejectionPenalty() * task.rejections());
            if (task.dueDate() == null || !delivered.isAfter(task.dueDate())) {
                onTimeCredit += weight;
                onTime++;
            } else {
                late++;
                if (task.lateWithReason()) {
                    onTimeCredit += weight * properties.lateWithReasonCredit();
                }
            }
            if (task.dueDate() != null) {
                speedWeight += weight;
                speedPoints += weight * speed(task);
            }
        }

        Double onTimeRate = deliveredWeight > 0 ? 100 * onTimeCredit / deliveredWeight : null;
        Double quality = deliveredWeight > 0 ? qualityPoints / deliveredWeight : null;
        Double completionRate = totalWeight > 0 ? 100 * completedWeight / totalWeight : null;
        Double overdueScore = totalWeight > 0 ? 100 - 100 * overdueWeight / totalWeight : null;
        Double speed = speedWeight > 0 ? speedPoints / speedWeight : null;

        double weightSum = 0;
        double pointSum = 0;
        double[][] parts = {
                {properties.onTimeWeight(), value(onTimeRate)},
                {properties.qualityWeight(), value(quality)},
                {properties.completionWeight(), value(completionRate)},
                {properties.overdueWeight(), value(overdueScore)},
                {properties.speedWeight(), value(speed)},
        };
        for (double[] part : parts) {
            if (!Double.isNaN(part[1])) {
                weightSum += part[0];
                pointSum += part[0] * part[1];
            }
        }
        Double raw = weightSum > 0 ? pointSum / weightSum : null;

        return new MemberScore(tasks.size(), completed, onTime, late, rejections, overdue, completedWeight,
                round(onTimeRate), round(quality), round(completionRate), round(overdueScore), round(speed), raw);
    }

    /**
     * Blends a raw score with the fixed baseline: few tasks stay near the baseline, many tasks are
     * almost entirely the person's own result. A fixed baseline (not the department average) keeps a
     * handful of perfect tasks in a strong team from outranking a long record. Null without a raw score.
     */
    public Double adjust(Double raw, int tasksCounted) {
        if (raw == null) {
            return null;
        }
        double k = properties.fairnessTasks();
        return round((tasksCounted * raw + k * properties.fairnessBaseline()) / (tasksCounted + k));
    }

    public boolean isRated(int tasksCounted) {
        return tasksCounted >= properties.minTasksForRating();
    }

    /** 1 to 5 in half steps. */
    public double stars(double score) {
        if (score >= 90) {
            return 5;
        }
        if (score >= 80) {
            return 4.5;
        }
        if (score >= 70) {
            return 4;
        }
        if (score >= 60) {
            return 3.5;
        }
        if (score >= 50) {
            return 3;
        }
        if (score >= 40) {
            return 2;
        }
        return 1;
    }

    private static boolean isOverdue(TaskFacts task, LocalDate today) {
        return task.dueDate() != null && task.dueDate().isBefore(today) && OWES_WORK.contains(task.status());
    }

    /** Late = 0; on the due date = 50; submitted as soon as the task started = 100. */
    private static double speed(TaskFacts task) {
        long left = ChronoUnit.DAYS.between(task.deliveredDate(), task.dueDate());
        if (left < 0) {
            return 0;
        }
        LocalDate start = task.startDate() != null ? task.startDate() : task.createdDate();
        long allowed = start != null ? Math.max(1, ChronoUnit.DAYS.between(start, task.dueDate())) : 1;
        return 50 + 50 * Math.min(1.0, (double) left / allowed);
    }

    private static boolean within(LocalDate date, LocalDate from, LocalDate to) {
        return !date.isBefore(from) && !date.isAfter(to);
    }

    private static double value(Double component) {
        return component != null ? component : Double.NaN;
    }

    static Double round(Double value) {
        return value != null ? Math.round(value * 10) / 10.0 : null;
    }
}
