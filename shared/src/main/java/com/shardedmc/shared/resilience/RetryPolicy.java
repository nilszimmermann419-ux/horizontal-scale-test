package com.shardedmc.shared.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Supplier;

public class RetryPolicy {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RetryPolicy.class);
    
    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double backoffMultiplier;
    private final Class<? extends Exception>[] retryableExceptions;
    
    @SafeVarargs
    public RetryPolicy(int maxAttempts, Duration initialDelay, Duration maxDelay, 
                       double backoffMultiplier, Class<? extends Exception>... retryableExceptions) {
        this.maxAttempts = maxAttempts;
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.backoffMultiplier = backoffMultiplier;
        this.retryableExceptions = retryableExceptions.length > 0 ? retryableExceptions : 
            new Class[]{Exception.class};
    }
    
    public RetryPolicy() {
        this(3, Duration.ofMillis(100), Duration.ofSeconds(5), 2.0);
    }
    
    public <T> T execute(Supplier<T> operation, String operationName) {
        int attempt = 1;
        Duration delay = initialDelay;
        Exception lastException = null;
        
        while (attempt <= maxAttempts) {
            try {
                T result = operation.get();
                if (attempt > 1) {
                    LOGGER.info("Operation '{}' succeeded on attempt {}", operationName, attempt);
                }
                return result;
            } catch (Exception e) {
                lastException = e;
                
                if (!isRetryable(e) || attempt >= maxAttempts) {
                    LOGGER.error("Operation '{}' failed permanently after {} attempts", 
                            operationName, attempt, e);
                    throw new RetryExhaustedException(
                            "Operation '" + operationName + "' failed after " + maxAttempts + " attempts", e);
                }
                
                LOGGER.warn("Operation '{}' failed on attempt {} ({}), retrying in {}ms...", 
                        operationName, attempt, e.getMessage(), delay.toMillis());
                
                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RetryExhaustedException("Retry interrupted", ie);
                }
                
                delay = calculateNextDelay(delay);
                attempt++;
            }
        }
        
        throw new RetryExhaustedException(
                "Operation '" + operationName + "' failed after " + maxAttempts + " attempts", lastException);
    }
    
    public void execute(Runnable operation, String operationName) {
        execute(() -> {
            operation.run();
            return null;
        }, operationName);
    }
    
    private boolean isRetryable(Exception e) {
        for (Class<? extends Exception> retryable : retryableExceptions) {
            if (retryable.isInstance(e)) {
                return true;
            }
        }
        return false;
    }
    
    private Duration calculateNextDelay(Duration currentDelay) {
        Duration nextDelay = currentDelay.multipliedBy((long) backoffMultiplier);
        if (nextDelay.compareTo(maxDelay) > 0) {
            return maxDelay;
        }
        return nextDelay;
    }
    
    public static class RetryExhaustedException extends RuntimeException {
        public RetryExhaustedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}