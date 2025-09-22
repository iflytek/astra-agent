package com.iflytek.astra.console.hub.util;

import com.iflytek.astra.console.hub.annotation.DistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Distributed lock usage example
 *
 * Demonstrates various usage patterns of the @DistributedLock annotation
 *
 * @author Astra Console Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class DistributedLockExample {

    /**
     * Example 1: Basic usage - User update operation using SpEL expression to generate lock key
     */
    @DistributedLock(key = "user:update:#{#userId}", description = "User information update lock")
    public void updateUser(String userId, String name) {
        log.info("Updating user information: userId={}, name={}", userId, name);
        // Simulate business processing
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("User information update completed: userId={}", userId);
    }

    /**
     * Example 2: Order processing - Custom timeout
     */
    @DistributedLock(key = "order:process:#{#orderId}", waitTime = 10, leaseTime = 60, timeUnit = TimeUnit.SECONDS, description = "Order processing lock")
    public void processOrder(String orderId) {
        log.info("Starting order processing: orderId={}", orderId);
        // Order processing logic
        log.info("Order processing completed: orderId={}", orderId);
    }

    /**
     * Example 3: Inventory deduction - Fair lock, returns null on failure
     */
    @DistributedLock(key = "inventory:deduct:#{#productId}", lockType = DistributedLock.LockType.FAIR, failStrategy = DistributedLock.FailStrategy.RETURN_NULL, waitTime = 5, leaseTime = 30, description = "Inventory deduction fair lock")
    public Boolean deductInventory(Long productId, Integer quantity) {
        log.info("Deducting inventory: productId={}, quantity={}", productId, quantity);

        // Simulate inventory check and deduction
        try {
            Thread.sleep(1000);
            // Actual business logic
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Example 4: Data statistics - Read lock, multiple read operations can execute concurrently
     */
    @DistributedLock(key = "statistics:read:#{#date}", lockType = DistributedLock.LockType.READ, waitTime = 3, leaseTime = 10, description = "Statistics data read lock")
    public String getStatistics(String date) {
        log.info("Reading statistics data: date={}", date);
        // Simulate data reading
        return "Statistics data: " + date;
    }

    /**
     * Example 5: Data update - Write lock, write operations are exclusive
     */
    @DistributedLock(key = "statistics:write:#{#date}", lockType = DistributedLock.LockType.WRITE, waitTime = 10, leaseTime = 30, description = "Statistics data write lock")
    public void updateStatistics(String date, String data) {
        log.info("Updating statistics data: date={}, data={}", date, data);
        // Simulate data update
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Statistics data update completed: date={}", date);
    }

    /**
     * Example 6: Complex SpEL expression - Using object properties to generate lock key
     */
    @DistributedLock(key = "complex:#{#request.spaceId}:#{#request.userId}:#{#request.operation}", waitTime = 8, leaseTime = 20, description = "Complex business operation lock")
    public void complexOperation(BusinessRequest request) {
        log.info("Executing complex business operation: {}", request);
        // Complex business logic
    }

    /**
     * Example 7: Continue execution when lock acquisition fails - For non-critical business
     */
    @DistributedLock(key = "non-critical:#{#taskId}", waitTime = 1, failStrategy = DistributedLock.FailStrategy.CONTINUE, description = "Non-critical task lock")
    public void nonCriticalTask(String taskId) {
        log.info("Executing non-critical task: taskId={}", taskId);
        // Business logic that executes even when lock cannot be acquired
    }

    /**
     * Business request object example
     */
    public static class BusinessRequest {
        private String spaceId;
        private String userId;
        private String operation;

        // Constructor, getters, setters etc. omitted

        public BusinessRequest(String spaceId, String userId, String operation) {
            this.spaceId = spaceId;
            this.userId = userId;
            this.operation = operation;
        }

        public String getSpaceId() {
            return spaceId;
        }

        public String getUserId() {
            return userId;
        }

        public String getOperation() {
            return operation;
        }

        @Override
        public String toString() {
            return String.format("BusinessRequest{spaceId='%s', userId='%s', operation='%s'}", spaceId, userId, operation);
        }
    }
}
