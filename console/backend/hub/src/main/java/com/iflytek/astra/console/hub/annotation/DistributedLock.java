package com.iflytek.astra.console.hub.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Distributed lock annotation. Distributed lock based on Redisson implementation, supports multiple
 * lock types such as reentrant locks, fair locks, etc.
 *
 * Usage examples: 1. Simple usage: @DistributedLock(key = "user:update:#{#userId}") 2. Custom
 * timeout: @DistributedLock(key = "order:#{#orderId}", waitTime = 10, leaseTime = 30) 3. Fair
 * lock: @DistributedLock(key = "fair:#{#id}", lockType = LockType.FAIR)
 *
 * @author Astra Console Team
 * @since 1.0.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    /**
     * Lock key value, supports SpEL expressions
     *
     * Supported SpEL expressions: - #{#parameterName}: Get method parameter -
     * #{#parameterName.property}: Get property of parameter object - #{@beanName.method()}: Call Spring
     * Bean method - #{T(ClassName).staticMethod()}: Call static method
     *
     * Examples: - "user:update:#{#userId}" - "order:#{#order.id}:#{#order.userId}" -
     * "global:#{T(System).currentTimeMillis()}"
     */
    String key();

    /**
     * Lock type. Default is reentrant lock
     */
    LockType lockType() default LockType.REENTRANT;

    /**
     * Maximum time to wait for lock acquisition, unit specified by timeUnit -1 means wait indefinitely
     * until lock is acquired 0 means don't wait, return failure immediately if lock cannot be acquired
     * Default 10 seconds
     */
    long waitTime() default 10L;

    /**
     * Lock auto-release time, unit specified by timeUnit -1 means no auto-release (requires manual
     * release or release after method execution) Default 30 seconds
     */
    long leaseTime() default 30L;

    /**
     * Time unit. Default is seconds
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * Handling strategy when lock acquisition fails
     */
    FailStrategy failStrategy() default FailStrategy.EXCEPTION;

    /**
     * Whether to log before method execution
     */
    boolean enableLog() default true;

    /**
     * Lock description information, used for logging and monitoring
     */
    String description() default "";

    /**
     * Lock type enumeration
     */
    enum LockType {
        /**
         * Reentrant lock (default) Same thread can acquire the same lock multiple times
         */
        REENTRANT,

        /**
         * Fair lock Acquire locks in the order of lock requests
         */
        FAIR,

        /**
         * Read-write lock - Read lock Multiple read operations can execute concurrently
         */
        READ,

        /**
         * Read-write lock - Write lock Write operations are exclusive
         */
        WRITE
    }

    /**
     * Handling strategy when lock acquisition fails
     */
    enum FailStrategy {
        /**
         * Throw exception (default)
         */
        EXCEPTION,

        /**
         * Return null directly
         */
        RETURN_NULL,

        /**
         * Continue execution (without acquiring lock) Note: Business logic needs to handle concurrency
         * issues by itself under this strategy
         */
        CONTINUE
    }
}
