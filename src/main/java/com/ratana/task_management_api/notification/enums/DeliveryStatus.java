package com.ratana.task_management_api.notification.enums;

public enum DeliveryStatus {
    PENDING,
    COMPLETED,
    FAILED,
    /** Exhausted max retry attempts — no further automatic retries. */
    DEAD_LETTER
}
