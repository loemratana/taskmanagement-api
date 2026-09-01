package com.ratana.task_management_api.enums;

public enum DeliveryStatus {
    PENDING,
    COMPLETED,
    FAILED,
    /** Exhausted max retry attempts — no further automatic retries. */
    DEAD_LETTER
}
