package com.ratana.task_management_api.common.observability;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Counts the SQL statements Hibernate prepares on the current thread, so the slow-request log can
 * say how many queries a request ran (dozens usually means an N+1). Costs one increment per query.
 */
public final class SqlStatementCounter implements StatementInspector {

    private static final ThreadLocal<int[]> COUNT = ThreadLocal.withInitial(() -> new int[1]);

    @Override
    public String inspect(String sql) {
        COUNT.get()[0]++;
        return sql;
    }

    /** Starts counting from zero on this thread. */
    public static void reset() {
        COUNT.get()[0] = 0;
    }

    public static int current() {
        return COUNT.get()[0];
    }

    /** Releases the counter so pooled request threads don't keep it. */
    public static void clear() {
        COUNT.remove();
    }
}
