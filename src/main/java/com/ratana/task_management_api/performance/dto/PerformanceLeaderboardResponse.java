package com.ratana.task_management_api.performance.dto;

import com.ratana.task_management_api.performance.enums.PerformancePeriod;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A department's ranking for one month or quarter. */
@Builder
public record PerformanceLeaderboardResponse(
        UUID departmentId,
        String departmentName,
        PerformancePeriod period,
        LocalDate from,
        LocalDate to,
        /* Average raw score of members with at least one counted task; null when nobody has one. */
        Double departmentAverage,
        int minTasksForRating,
        /* Rated members by rank, then unrated members by name. */
        List<Entry> entries
) {

    @Builder
    public record Entry(
            /* 1-based; null while not rated yet. */
            Integer rank,
            UUID userId,
            String fullName,
            String avatarUrl,
            String positionName,
            boolean rated,
            /* Final 0-100 score after the fairness adjustment; null with no counted tasks. */
            Double score,
            /* 1 to 5 in half steps; null while not rated yet. */
            Double stars,
            Double rawScore,
            Counts counts,
            Components components
    ) {
    }

    @Builder
    public record Counts(int tasks, int completed, int onTime, int late, int rejections, int overdue) {
    }

    /** Each 0-100; null when there was nothing to measure. */
    @Builder
    public record Components(Double onTime, Double quality, Double completion, Double overdue, Double speed) {
    }
}
