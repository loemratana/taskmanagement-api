package com.ratana.task_management_api.performance.event;

import java.util.UUID;

/**
 * An award was saved; listeners notify the department after the transaction commits.
 *
 * @param managerId the department manager, shown as the sender; null when the department has none
 */
public record PerformanceAwardPublishedEvent(UUID awardId, UUID departmentId, UUID managerId,
                                             String title, String message) {
}
