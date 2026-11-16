package com.ratana.task_management_api.task.dto.response;

import java.util.Map;

/**
 * Exact task counts for the caller's visible tasks matching a filter (same rules as GET /tasks).
 *
 * @param total    all matching tasks
 * @param overdue  due before today and not DONE/APPROVED/CANCELLED
 * @param byStatus count per TaskStatus name; every status is present, zero included
 */
public record TaskCountsResponse(long total, long overdue, Map<String, Long> byStatus) {
}
