package com.shardedmc.shared.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class CircuitBreaker {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CircuitBreaker.class);
    
    public enum State {
        CLOSED,     // Normal operation
        OPEN,       // Failing, rejecting requests
        HALF_OPEN   // Testing if service recovered
    }
    
    private final String name;
    private final int failureThreshold;
    private final Duration openDuration;
    private final int successThreshold;
    
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicReference<Instant> lastFailureTime = new AtomicReference<>();
    
    public CircuitBreaker(String name, int failureThreshold, Duration openDuration, int successThreshold) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.successThreshold = successThreshold;
    }
    
    public CircuitBreaker(String name) {
        this(name, 5, Duration.ofSeconds(30), 3);
    }
    
    public State getState() {
        Instant failureTime = lastFailureTime.get();
        if (state.get() == State.OPEN && failureTime != null) {
            if (Duration.between(failureTime, Instant.now()).compareTo(openDuration) >= 0) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    LOGGER.info("Circuit breaker '{}' moved to HALF_OPEN state", name);
                    successCount.set(0);
                    failureCount.set(0);
                }
            }
        }
        return state.get();
    }
    
    public boolean allowRequest() {
        State currentState = getState();
        return currentState == State.CLOSED || currentState == State.HALF_OPEN;
    }
    
    public void recordSuccess() {
        if (state.get() == State.HALF_OPEN) {
            int successes = successCount.incrementAndGet();
            if (successes >= successThreshold) {
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    LOGGER.info("Circuit breaker '{}' moved to CLOSED state", name);
                    failureCount.set(0);
                    successCount.set(0);
                }
            }
        } else {
            failureCount.set(0);
            successCount.set(0);
        }
    }
    
    public void recordFailure() {
        lastFailureTime.set(Instant.now());
        
        if (state.get() == State.HALF_OPEN) {
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                LOGGER.warn("Circuit breaker '{}' moved to OPEN state (half-open test failed)", name);
            }
        } else {
            int failures = failureCount.incrementAndGet();
            if (failures >= failureThreshold) {
                if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                    LOGGER.warn("Circuit breaker '{}' moved to OPEN state ({} failures)", name, failures);
                }
            }
        }
    }
    
    public <T> T execute(Supplier<T> operation) {
        if (!allowRequest()) {
            throw new CircuitBreakerOpenException("Circuit breaker '" + name + "' is OPEN");
        }
        
        try {
            T result = operation.get();
            recordSuccess();
            return result;
        } catch (Exception e) {
            recordFailure();
            throw e;
        }
    }
    
    public void execute(Runnable operation) {
        if (!allowRequest()) {
            throw new CircuitBreakerOpenException("Circuit breaker '" + name + "' is OPEN");
        }
        
        try {
            operation.run();
            recordSuccess();
        } catch (Exception e) {
            recordFailure();
            throw e;
        }
    }
    
    public String getName() {
        return name;
    }
    
    public int getFailureCount() {
        return failureCount.get();
    }
    
    public int getSuccessCount() {
        return successCount.get();
    }
    
    public boolean isOpen() {
        return getState() == State.OPEN;
    }
    
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}