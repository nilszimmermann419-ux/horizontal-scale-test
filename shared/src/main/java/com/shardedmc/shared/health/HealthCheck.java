package com.shardedmc.shared.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class HealthCheck {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheck.class);
    
    private final Map<String, HealthIndicator> indicators = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> {
                Thread t = new Thread(r, "health-check-scheduler");
                t.setDaemon(true);
                return t;
            }
    );
    
    private volatile HealthStatus overallStatus = HealthStatus.HEALTHY;
    private volatile Instant lastCheckTime = Instant.now();
    
    public HealthCheck() {
        // Default system health indicators
        registerIndicator("memory", this::checkMemory);
        registerIndicator("threads", this::checkThreads);
    }
    
    public void registerIndicator(String name, Supplier<HealthStatus> check) {
        indicators.put(name, new HealthIndicator(name, check));
        LOGGER.debug("Registered health indicator: {}", name);
    }
    
    public void start(long intervalSeconds) {
        scheduler.scheduleAtFixedRate(this::runChecks, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        LOGGER.info("Health checks started with interval: {}s", intervalSeconds);
    }
    
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("Health checks stopped");
    }
    
    private void runChecks() {
        boolean allHealthy = true;
        
        for (HealthIndicator indicator : indicators.values()) {
            try {
                HealthStatus status = indicator.check();
                indicator.lastStatus = status;
                indicator.lastCheckTime = Instant.now();
                
                if (status == HealthStatus.UNHEALTHY) {
                    allHealthy = false;
                    LOGGER.warn("Health indicator '{}' is UNHEALTHY", indicator.name);
                }
            } catch (Exception e) {
                allHealthy = false;
                indicator.lastStatus = HealthStatus.UNHEALTHY;
                indicator.lastCheckTime = Instant.now();
                LOGGER.error("Health indicator '{}' check failed", indicator.name, e);
            }
        }
        
        overallStatus = allHealthy ? HealthStatus.HEALTHY : HealthStatus.UNHEALTHY;
        lastCheckTime = Instant.now();
    }
    
    public HealthStatus getOverallStatus() {
        return overallStatus;
    }
    
    public Map<String, HealthIndicator> getIndicators() {
        return new ConcurrentHashMap<>(indicators);
    }
    
    public Instant getLastCheckTime() {
        return lastCheckTime;
    }
    
    public HealthReport generateReport() {
        return new HealthReport(overallStatus, lastCheckTime, indicators);
    }
    
    private HealthStatus checkMemory() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double usagePercent = (usedMemory * 100.0) / maxMemory;
        
        if (usagePercent > 90) {
            LOGGER.warn("Memory usage is critical: {}%", usagePercent);
            return HealthStatus.UNHEALTHY;
        } else if (usagePercent > 80) {
            LOGGER.warn("Memory usage is high: {}%", usagePercent);
            return HealthStatus.DEGRADED;
        }
        
        return HealthStatus.HEALTHY;
    }
    
    private HealthStatus checkThreads() {
        int threadCount = java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount();
        if (threadCount > 500) {
            LOGGER.warn("Thread count is high: {}", threadCount);
            return HealthStatus.DEGRADED;
        }
        return HealthStatus.HEALTHY;
    }
    
    public enum HealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY
    }
    
    public static class HealthIndicator {
        final String name;
        final Supplier<HealthStatus> check;
        volatile HealthStatus lastStatus = HealthStatus.HEALTHY;
        volatile Instant lastCheckTime = Instant.now();
        
        public HealthIndicator(String name, Supplier<HealthStatus> check) {
            this.name = name;
            this.check = check;
        }
        
        public HealthStatus check() {
            return check.get();
        }
        
        public String getName() { return name; }
        public HealthStatus getLastStatus() { return lastStatus; }
        public Instant getLastCheckTime() { return lastCheckTime; }
    }
    
    public static class HealthReport {
        private final HealthStatus overallStatus;
        private final Instant checkTime;
        private final Map<String, HealthIndicator> indicators;
        
        public HealthReport(HealthStatus overallStatus, Instant checkTime, 
                           Map<String, HealthIndicator> indicators) {
            this.overallStatus = overallStatus;
            this.checkTime = checkTime;
            this.indicators = new ConcurrentHashMap<>(indicators);
        }
        
        public HealthStatus getOverallStatus() { return overallStatus; }
        public Instant getCheckTime() { return checkTime; }
        public Map<String, HealthIndicator> getIndicators() { return indicators; }
        
        public boolean isHealthy() {
            return overallStatus == HealthStatus.HEALTHY;
        }
    }
}