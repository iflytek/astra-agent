package com.iflytek.astra.console.toolkit.config.jooq;

import java.sql.SQLException;
import java.util.*;

public class JooqRetry {

    // PG common retryable SQLState: 40001 serialization failure; 40P01 deadlock; 55P03 lock not
    // available; 57014 query cancelled; 53300 too many connections
    private static final Set<String> RETRYABLE_STATES = new HashSet<>(Arrays.asList("40001", "40P01", "55P03", "57014", "53300"));

    public static boolean isRetryable(Throwable t) {
        Throwable root = unwrap(t);
        if (root instanceof SQLException) {
            String state = ((SQLException) root).getSQLState();
            return state != null && RETRYABLE_STATES.contains(state);
        }
        return false;
    }

    public static Throwable unwrap(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur)
            cur = cur.getCause();
        return cur;
    }

    public static void sleepBackoff(int attempt, long baseMillis, long maxMillis) {
        long sleep = Math.min(maxMillis, baseMillis * (1L << Math.min(6, attempt))); // Exponential backoff with upper limit
        try {
            Thread.sleep(sleep);
        } catch (InterruptedException ignored) {
        }
    }
}
