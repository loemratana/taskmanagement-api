package com.ratana.task_management_api.performance.config;

import com.ratana.task_management_api.task.enums.Priority;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;

/**
 * Tuning for the performance score and star rating ({@code app.performance.*}).
 * The five component weights are relative: they need not add up to 100.
 */
@ConfigurationProperties(prefix = "app.performance")
public record PerformanceProperties(
        /* Business timezone: due dates and "today" are dates in this zone. */
        @DefaultValue("Asia/Phnom_Penh") ZoneId zone,

        /* First submission on or before the due date. */
        @DefaultValue("30") double onTimeWeight,
        /* Fewer rejection rounds per task. */
        @DefaultValue("25") double qualityWeight,
        /* Approved or done out of the tasks that count. */
        @DefaultValue("20") double completionWeight,
        /* Unfinished tasks past their due date. */
        @DefaultValue("15") double overdueWeight,
        /* How early, relative to the time allowed, work was submitted. */
        @DefaultValue("10") double speedWeight,

        /* Quality points lost per rejected submission on a task (100 = perfect). */
        @DefaultValue("25") double rejectionPenalty,
        /* Share of on-time credit (0..1) a late submission earns when it gave a late reason. */
        @DefaultValue("0") double lateWithReasonCredit,
        /* true: collaborators on a shared task get the same credit as its primary assignee. */
        @DefaultValue("true") boolean countCollaborators,
        /* Below this many counted tasks a person is listed as "not rated yet" (no stars, no rank). */
        @DefaultValue("3") int minTasksForRating,
        /*
         * Fairness: a score is blended with fairnessBaseline as if the person had fairnessTasks extra tasks
         * at that score, so a few lucky tasks can't outrank a long, consistent record.
         */
        @DefaultValue("5") double fairnessTasks,
        @DefaultValue("70") double fairnessBaseline,

        @DefaultValue("0.75") double lowPriorityWeight,
        @DefaultValue("1") double mediumPriorityWeight,
        @DefaultValue("1.5") double highPriorityWeight,
        @DefaultValue("2") double urgentPriorityWeight
) {

    public double priorityWeight(Priority priority) {
        if (priority == null) {
            return mediumPriorityWeight;
        }
        return switch (priority) {
            case LOW -> lowPriorityWeight;
            case MEDIUM -> mediumPriorityWeight;
            case HIGH -> highPriorityWeight;
            case URGENT -> urgentPriorityWeight;
        };
    }
}
