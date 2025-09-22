package com.iflytek.astra.console.hub.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Distributed lock configuration class
 *
 * Ensures proper initialization and configuration of distributed lock related components Enables
 * AspectJ auto proxy to support AOP processing of @DistributedLock annotation
 *
 * @author Astra Console Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ConditionalOnBean(RedissonClient.class)
public class DistributedLockConfig {

    private final RedissonClient redissonClient;

    public DistributedLockConfig(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * Validate configuration after initialization
     */
    @PostConstruct
    public void validateConfiguration() {
        try {
            // Validate RedissonClient connection
            if (redissonClient != null && !redissonClient.isShutdown()) {
                log.info("Distributed lock configuration initialized, RedissonClient connection normal");

                // Optional: test basic Redis connection
                String testKey = "distributed-lock:config:test";
                redissonClient.getBucket(testKey).set("test", java.time.Duration.ofSeconds(10));
                redissonClient.getBucket(testKey).delete();

                log.info("Distributed lock Redis connection test passed");
            } else {
                log.error("RedissonClient not properly initialized or has been closed");
            }
        } catch (Exception e) {
            log.error("Distributed lock configuration validation failed: {}", e.getMessage(), e);
            // Don't throw exception to avoid affecting application startup, but log error for troubleshooting
        }
    }
}
