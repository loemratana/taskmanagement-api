package com.ratana.task_management_api.announcement.dto.response;

import com.ratana.task_management_api.announcement.enums.AnnouncementType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Chart colors ("fill") stay in the frontend. */
@Schema(description = "Data for the announcement analytics dashboard")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementAnalyticsResponse {

    private List<MonthlyCount> monthlyCounts;
    private List<TypeCount> typeDistribution;
    private List<MonthlyRate> readRateTrend;
    private List<DepartmentEngagement> departmentEngagement;
    private List<MostViewed> mostViewed;
    private List<AnnouncementResponse> recentPublished;
    private List<AnnouncementResponse> upcomingScheduled;
    private List<RecentlyViewed> recentlyViewed;

    /** month as "yyyy-MM" */
    public record MonthlyCount(String month, long count) {}

    public record TypeCount(AnnouncementType type, long count) {}

    /** rate 0-100 */
    public record MonthlyRate(String month, double rate) {}

    /** rate 0-100 */
    public record DepartmentEngagement(UUID departmentId, String department, double rate) {}

    public record MostViewed(UUID id, String title, long views) {}

    public record RecentlyViewed(UUID id, String title, Instant viewedAt) {}
}
