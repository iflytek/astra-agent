// com.iflytek.astra.console.toolkit.config.thread.AsyncExecutorConfig
package com.iflytek.astra.console.toolkit.config.thread;

import com.iflytek.astra.console.toolkit.config.properties.AsyncExecutorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Business pool (scalable) handles the actual concurrent workload
 */
@Slf4j
@Configuration
@EnableAsync
@EnableConfigurationProperties(AsyncExecutorProperties.class)
@RequiredArgsConstructor
public class AsyncExecutorConfig implements AsyncConfigurer {

    private final AsyncExecutorProperties props;

    @Bean(name = "asyncExecutor")
    public ThreadPoolTaskExecutor asyncExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(props.getCorePoolSize());
        exec.setMaxPoolSize(props.getMaxPoolSize());
        exec.setQueueCapacity(props.getQueueCapacity());
        exec.setKeepAliveSeconds(props.getKeepAliveSeconds());
        exec.setAllowCoreThreadTimeOut(props.isAllowCoreThreadTimeout());
        exec.setThreadNamePrefix(props.getThreadNamePrefix());
        exec.setAwaitTerminationSeconds(props.getAwaitTerminationSeconds());
        exec.setWaitForTasksToCompleteOnShutdown(props.isWaitForTasksToCompleteOnShutdown());
        exec.setRejectedExecutionHandler(mapRejectPolicy(props.getRejectionPolicy()));
        exec.setTaskDecorator(r -> {
            // MDC/TraceId propagation can be done here
            return r;
        });
        exec.initialize();

        log.info("[async-executor] init: core={}, max={}, queue={}, keepAlive={}s, prefix={}, reject={}",
                        props.getCorePoolSize(), props.getMaxPoolSize(), props.getQueueCapacity(),
                        props.getKeepAliveSeconds(), props.getThreadNamePrefix(), props.getRejectionPolicy());
        return exec;
    }

    @Override
    public ThreadPoolTaskExecutor getAsyncExecutor() {
        // As the default executor for @Async
        return asyncExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // Catch uncaught exceptions thrown by void @Async methods
        return (ex, method, params) -> log.warn("[@Async] method={} error={}, args={}", method.getName(), ex.getMessage(), params, ex);
    }

    private RejectedExecutionHandler mapRejectPolicy(String policy) {
        if (policy == null)
            return new ThreadPoolExecutor.CallerRunsPolicy();
        switch (policy) {
            case "Abort":
                return new ThreadPoolExecutor.AbortPolicy();
            case "Discard":
                return new ThreadPoolExecutor.DiscardPolicy();
            case "DiscardOldest":
                return new ThreadPoolExecutor.DiscardOldestPolicy();
            case "CallerRuns":
            default:
                return new ThreadPoolExecutor.CallerRunsPolicy();
        }
    }
}
