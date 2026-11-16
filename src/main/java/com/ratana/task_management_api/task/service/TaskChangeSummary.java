package com.ratana.task_management_api.task.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Turns an edit's before/after values into one line for the people on the task, e.g.
 * "due date 20 Nov 2026 → 25 Nov 2026, priority MEDIUM → HIGH".
 */
final class TaskChangeSummary {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private TaskChangeSummary() {
    }

    /** Null when nothing worth telling the assignee changed. */
    static String of(Map<String, Object> before, Map<String, Object> after) {
        List<String> parts = new ArrayList<>();
        if (after.containsKey("dueDate")) {
            parts.add("due date " + date(before.get("dueDate")) + " → " + date(after.get("dueDate")));
        }
        if (after.containsKey("priority")) {
            parts.add("priority " + text(before.get("priority")) + " → " + text(after.get("priority")));
        }
        if (after.containsKey("title")) {
            parts.add("title \"" + text(before.get("title")) + "\" → \"" + text(after.get("title")) + "\"");
        }
        if (after.containsKey("description")) {
            parts.add("the description");
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static String date(Object value) {
        return value instanceof LocalDate date ? date.format(DATE) : "none";
    }

    private static String text(Object value) {
        return Objects.toString(value, "none");
    }
}
