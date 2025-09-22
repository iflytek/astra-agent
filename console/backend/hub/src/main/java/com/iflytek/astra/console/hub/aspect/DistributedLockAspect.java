package com.iflytek.astra.console.hub.aspect;

import com.iflytek.astra.console.hub.annotation.DistributedLock;
import com.iflytek.astra.console.hub.exception.DistributedLockException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Distributed lock aspect
 *
 * Handles @DistributedLock annotation, implements distributed lock acquisition and release logic.
 * Supports multiple lock types: reentrant lock, fair lock, read-write lock. Supports SpEL
 * expression parsing for lock key values
 *
 * @author Astra Console Team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
public class DistributedLockAspect {

    private final RedissonClient redissonClient;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Autowired
    public DistributedLockAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * Distributed lock around advice
     *
     * @param point Join point
     * @param distributedLock Distributed lock annotation
     * @return Method execution result
     * @throws Throwable Method execution exception
     */
    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint point, DistributedLock distributedLock) throws Throwable {
        String lockKey = parseLockKey(distributedLock.key(), point);
        RLock lock = getLock(lockKey, distributedLock.lockType());

        if (distributedLock.enableLog()) {
            logLockOperation(lockKey, distributedLock, "attempting to acquire");
        }

        return executeLockLogic(point, distributedLock, lockKey, lock);
    }

    /**
     * Main method for executing lock logic
     */
    private Object executeLockLogic(ProceedingJoinPoint point, DistributedLock distributedLock, String lockKey, RLock lock) throws Throwable {
        boolean acquired = false;
        long startTime = System.currentTimeMillis();

        try {
            acquired = tryLock(lock, distributedLock);

            if (!acquired) {
                return handleLockFailure(lockKey, distributedLock, point);
            }

            logSuccessfulAcquisition(distributedLock, lockKey, startTime);
            return point.proceed();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw createLockException(lockKey, distributedLock, e);
        } catch (Exception e) {
            log.error("Distributed lock execution exception: key={}, message={}", lockKey, e.getMessage(), e);
            throw e;
        } finally {
            releaseLockSafely(distributedLock, lockKey, lock, acquired, startTime);
        }
    }

    /**
     * Log successful lock acquisition
     */
    private void logSuccessfulAcquisition(DistributedLock distributedLock, String lockKey, long startTime) {
        if (distributedLock.enableLog()) {
            long acquireTime = System.currentTimeMillis() - startTime;
            log.info("Successfully acquired distributed lock: key={}, description={}, acquireTime={}ms", lockKey, distributedLock.description(), acquireTime);
        }
    }

    /**
     * Create lock exception
     */
    private DistributedLockException createLockException(String lockKey, DistributedLock distributedLock, InterruptedException e) {
        return new DistributedLockException(lockKey, DistributedLockException.LockErrorType.ACQUIRE_TIMEOUT, "Thread interrupted while acquiring lock", e);
    }

    /**
     * Release lock safely
     */
    private void releaseLockSafely(DistributedLock distributedLock, String lockKey, RLock lock, boolean acquired, long startTime) {
        if (acquired && lock.isHeldByCurrentThread()) {
            try {
                lock.unlock();
                if (distributedLock.enableLog()) {
                    long totalTime = System.currentTimeMillis() - startTime;
                    log.info("Successfully released distributed lock: key={}, totalTime={}ms", lockKey, totalTime);
                }
            } catch (Exception e) {
                log.error("Failed to release distributed lock: key={}, message={}", lockKey, e.getMessage(), e);
                throw new DistributedLockException(lockKey, DistributedLockException.LockErrorType.RELEASE_FAILED, "Lock release failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Parse lock key, supports SpEL expressions
     */
    private String parseLockKey(String keyExpression, ProceedingJoinPoint point) {
        try {
            if (!keyExpression.contains("#{")) {
                // Simple string, return directly
                return keyExpression;
            }

            // SpEL expression parsing
            MethodSignature signature = (MethodSignature) point.getSignature();
            Method method = signature.getMethod();
            Object[] args = point.getArgs();

            EvaluationContext context = new MethodBasedEvaluationContext(point.getTarget(), method, args, nameDiscoverer);

            Expression expression = parser.parseExpression(keyExpression);
            Object result = expression.getValue(context);

            return result != null ? result.toString() : keyExpression;
        } catch (Exception e) {
            log.error("Failed to parse lock key: keyExpression={}, error={}", keyExpression, e.getMessage(), e);
            throw new DistributedLockException(keyExpression, DistributedLockException.LockErrorType.KEY_PARSE_FAILED, "Lock key parsing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get corresponding lock object based on lock type
     */
    private RLock getLock(String lockKey, DistributedLock.LockType lockType) {
        try {
            return switch (lockType) {
                case REENTRANT -> redissonClient.getLock(lockKey);
                case FAIR -> redissonClient.getFairLock(lockKey);
                case READ -> {
                    RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(lockKey);
                    yield readWriteLock.readLock();
                }
                case WRITE -> {
                    RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(lockKey);
                    yield readWriteLock.writeLock();
                }
            };
        } catch (Exception e) {
            log.error("Failed to get lock object: key={}, lockType={}, error={}", lockKey, lockType, e.getMessage(), e);
            throw new DistributedLockException(lockKey,
                DistributedLockException.LockErrorType.REDIS_CONNECTION_ERROR,
                "Failed to get lock object: " + e.getMessage(), e);
        }
    }

    /**
     * Try to acquire lock
     */
    private boolean tryLock(RLock lock, DistributedLock distributedLock) throws InterruptedException {
        if (distributedLock.waitTime() <= 0) {
            // Don't wait, try to acquire lock immediately
            if (distributedLock.leaseTime() > 0) {
                return lock.tryLock(0, distributedLock.leaseTime(), distributedLock.timeUnit());
            } else {
                return lock.tryLock();
            }
        } else {
            // Wait for specified time to acquire lock
            if (distributedLock.leaseTime() > 0) {
                return lock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(), distributedLock.timeUnit());
            } else {
                return lock.tryLock(distributedLock.waitTime(), distributedLock.timeUnit());
            }
        }
    }

    /**
     * Handle lock acquisition failure
     */
    private Object handleLockFailure(String lockKey, DistributedLock distributedLock,
                    ProceedingJoinPoint point) throws Throwable {
        logLockFailure(lockKey, distributedLock);
        return executeFailureStrategy(lockKey, distributedLock, point);
    }

    private void logLockFailure(String lockKey, DistributedLock distributedLock) {
        String message = String.format("Failed to acquire distributed lock: key=%s, waitTime=%d%s",
                        lockKey, distributedLock.waitTime(), distributedLock.timeUnit().name().toLowerCase());
        log.warn(message);
    }

    private Object executeFailureStrategy(String lockKey, DistributedLock distributedLock,
                                        ProceedingJoinPoint point) throws Throwable {
        return switch (distributedLock.failStrategy()) {
            case EXCEPTION -> throw new DistributedLockException(lockKey,
                DistributedLockException.LockErrorType.ACQUIRE_TIMEOUT,
                "Distributed lock acquisition timeout");
            case RETURN_NULL -> null;
            case CONTINUE -> {
                log.warn("Distributed lock acquisition failed, but continuing business logic execution: key={}", lockKey);
                yield point.proceed();
            }
        };
    }

    /**
     * Log lock operation
     */
    private void logLockOperation(String lockKey, DistributedLock distributedLock, String operation) {
        log.info("Distributed lock operation: operation={}, key={}, lockType={}, waitTime={}s, leaseTime={}s, " + "failStrategy={}, description={}", operation, lockKey, distributedLock.lockType(),
                        getTimeInSeconds(distributedLock.waitTime(), distributedLock.timeUnit()), getTimeInSeconds(distributedLock.leaseTime(), distributedLock.timeUnit()), distributedLock.failStrategy(), distributedLock.description());
    }

    /**
     * Convert time to seconds
     */
    private long getTimeInSeconds(long time, TimeUnit timeUnit) {
        return timeUnit.toSeconds(time);
    }
}
