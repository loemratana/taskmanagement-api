package com.ratana.task_management_api.announcement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Counters for the announcements dashboard cards")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementStatsResponse {
    private long total;
    private long published;
    private long scheduled;
    private long pinned;
    /** 0 until calendar events are implemented. */
    private long upcomingEvents;
    /** 0 until holidays are implemented. */
    private long holidaysThisMonth;
    private long totalViews;
    /** 0-100, readers / targeted recipients across published announcements. */
    private double readRate;
    private long unreadEmployees;
}
