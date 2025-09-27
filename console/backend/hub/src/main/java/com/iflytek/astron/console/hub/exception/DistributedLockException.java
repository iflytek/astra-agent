package com.iflytek.astron.console.hub.exception;

/**
 * Distributed lock exception
 *
 * Thrown when exceptions occur during distributed lock acquisition, release, or processing
 *
 * @author Astron Console Team
 * @since 1.0.0
 */
public class DistributedLockException extends RuntimeException {

    private final String lockKey;
    private final LockErrorType errorType;

    public DistributedLockException(String lockKey, LockErrorType errorType, String message) {
        super(message);
        this.lockKey = lockKey;
        this.errorType = errorType;
    }

    public DistributedLockException(String lockKey, LockErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.lockKey = lockKey;
        this.errorType = errorType;
    }

    public String getLockKey() {
        return lockKey;
    }

    public LockErrorType getErrorType() {
        return errorType;
    }

    /**
     * Lock error type enumeration
     */
    public enum LockErrorType {
        /**
         * Lock acquisition timeout
         */
        ACQUIRE_TIMEOUT("Lock acquisition timeout"),

        /**
         * Lock release failed
         */
        RELEASE_FAILED("Lock release failed"),

        /**
         * Lock key parsing failed
         */
        KEY_PARSE_FAILED("Lock key parsing failed"),

        /**
         * Redis connection error
         */
        REDIS_CONNECTION_ERROR("Redis connection error"),

        /**
         * Lock configuration error
         */
        CONFIG_ERROR("Lock configuration error"),

        /**
         * Other unknown error
         */
        UNKNOWN_ERROR("Unknown error");

        private final String description;

        LockErrorType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @Override
    public String toString() {
        return String.format("DistributedLockException{lockKey='%s', errorType=%s, message='%s'}", lockKey, errorType, getMessage());
    }
}
