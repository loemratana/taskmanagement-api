package com.ratana.task_management_api.performance.dto;

import com.ratana.task_management_api.performance.enums.PerformancePeriod;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A published (frozen) top-performer announcement. */
@Builder
public record PerformanceAwardResponse(
        UUID id,
        UUID departmentId,
        String departmentName,
        PerformancePeriod period,
        int year,
        /* Month (1-12) or quarter (1-4); null for YEARLY. */
        Integer number,
        /* "November 2026", "Q4 2026" or "2026". */
        String label,
        LocalDate from,
        LocalDate to,
        Instant publishedAt,
        /* How many people were rated (the full ranking's length). */
        int ratedCount,
        /* The top 3 for the popup, or everyone rated for GET /awards/{id}. */
        List<Entry> entries,
        /* The caller's own place, when they were rated; null otherwise. */
        Entry myEntry
) {

    @Builder
    public record Entry(int rank, UUID userId, String fullName, String avatarUrl, String positionName,
                        double score, double stars, int tasks) {
    }
}
