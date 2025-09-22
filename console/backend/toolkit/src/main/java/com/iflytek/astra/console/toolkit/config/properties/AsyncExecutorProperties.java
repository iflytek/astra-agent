// com.iflytek.astra.console.toolkit.config.properties.AsyncExecutorProperties
package com.iflytek.astra.console.toolkit.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "task.executor")
public class AsyncExecutorProperties {
    private int corePoolSize = 4;
    private int maxPoolSize = 8;
    private int queueCapacity = 1000;
    private int keepAliveSeconds = 60;
    private boolean allowCoreThreadTimeout = false;
    private String threadNamePrefix = "app-async-";
    private int awaitTerminationSeconds = 20;
    private boolean waitForTasksToCompleteOnShutdown = true;
    /** Abort / CallerRuns / Discard / DiscardOldest */
    private String rejectionPolicy = "CallerRuns";
}
