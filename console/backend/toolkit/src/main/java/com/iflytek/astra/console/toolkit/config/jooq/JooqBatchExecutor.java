package com.iflytek.astra.console.toolkit.config.jooq;

import org.jooq.*;

import java.util.*;
import java.util.function.Function;

public class JooqBatchExecutor {

    public static class RowError {
        public final int index;
        public final Map<String, Object> row;
        public final String message;

        public RowError(int index, Map<String, Object> row, String message) {
            this.index = index;
            this.row = row;
            this.message = message;
        }
    }

    public static class ResultSummary {
        public int success;
        public int failed;
        public final List<RowError> errors = new ArrayList<>();
    }

    /**
     * Execute in chunks. Each row independently builds a Query; failures do not block; limited retries
     * for retryable exceptions.
     */
    // JooqBatchExecutor.java
    public static ResultSummary executeInChunks(
                    DSLContext dsl,
                    String tableName,
                    List<Map<String, Object>> rows,
                    int chunkSize,
                    int maxRetries,
                    Function<Map<String, Object>, Query> builder,
                    SqlSender sender // ★ New: Delegate "how to execute SQL" to the caller
    ) {
        ResultSummary sum = new ResultSummary();
        if (rows == null || rows.isEmpty())
            return sum;

        for (int start = 0; start < rows.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, rows.size());
            List<Map<String, Object>> part = rows.subList(start, end);

            for (int i = 0; i < part.size(); i++) {
                Map<String, Object> row = part.get(i);
                int globalIdx = start + i;
                int attempts = 0;
                while (true) {
                    try {
                        Query q = builder.apply(row);
                        // ★ No longer q.execute(), but render template + parameters
                        String sql = q.getSQL(); // Template with ?
                        List<Object> params = q.getBindValues(); // Bind parameters
                        // Send to core system
                        sender.send(sql, params);
                        sum.success++;
                        break;
                    } catch (Throwable ex) {
                        attempts++;
                        if (attempts <= maxRetries && JooqRetry.isRetryable(ex)) {
                            JooqRetry.sleepBackoff(attempts, 50, 1000);
                            continue;
                        }
                        sum.failed++;
                        sum.errors.add(new RowError(globalIdx, row, JooqRetry.unwrap(ex).getMessage()));
                        break;
                    }
                }
            }
        }
        return sum;
    }
}
