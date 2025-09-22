// HeartbeatPoolProperties.java
package com.iflytek.astra.console.toolkit.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "task.scheduling")
public class SchedulingPoolProperties {
    private int poolSize = 2;
    private String threadNamePrefix = "app-scheduler-";
    private int awaitTerminationSeconds = 10;
    private boolean waitForTasksToCompleteOnShutdown = true;
}
