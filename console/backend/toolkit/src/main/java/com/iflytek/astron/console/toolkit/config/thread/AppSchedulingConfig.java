package com.iflytek.astron.console.toolkit.config.thread;

import com.iflytek.astron.console.toolkit.config.properties.SchedulingPoolProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Scheduling pool (fixed size) only runs scheduled tasks
 */
@Slf4j
@Configuration
@EnableScheduling
@EnableConfigurationProperties(SchedulingPoolProperties.class)
@RequiredArgsConstructor
public class AppSchedulingConfig implements SchedulingConfigurer {

    private final SchedulingPoolProperties props;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(props.getPoolSize());

        scheduler.setThreadNamePrefix(props.getThreadNamePrefix());
        scheduler.setAwaitTerminationSeconds(props.getAwaitTerminationSeconds());
        scheduler.setWaitForTasksToCompleteOnShutdown(props.isWaitForTasksToCompleteOnShutdown());
        scheduler.setErrorHandler(ex -> log.warn("[app-scheduler] task error: {}", ex.getMessage(), ex));
        scheduler.initialize();
        registrar.setTaskScheduler(scheduler);
        log.info("[app-scheduler] init: size={}, prefix={}", props.getPoolSize(), props.getThreadNamePrefix());
    }
}
