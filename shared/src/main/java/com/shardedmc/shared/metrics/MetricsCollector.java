package com.shardedmc.shared.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.core.instrument.binder.jvm.*;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class MetricsCollector {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsCollector.class);
    
    private final PrometheusMeterRegistry registry;
    private final String serviceName;
    private final String serviceId;
    private final ConcurrentHashMap<String, Meter> meterCache = new ConcurrentHashMap<>();
    private final JvmGcMetrics jvmGcMetrics;
    private final JvmHeapPressureMetrics jvmHeapPressureMetrics;
    
    public MetricsCollector(String serviceName, String serviceId) {
        this.serviceName = serviceName;
        this.serviceId = serviceId;
        this.registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        
        // Add common tags
        registry.config().commonTags("service", serviceName, "instance", serviceId);
        
        // Bind JVM metrics
        new ClassLoaderMetrics().bindTo(registry);
        new JvmMemoryMetrics().bindTo(registry);
        this.jvmGcMetrics = new JvmGcMetrics();
        this.jvmGcMetrics.bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new JvmCompilationMetrics().bindTo(registry);
        this.jvmHeapPressureMetrics = new JvmHeapPressureMetrics();
        this.jvmHeapPressureMetrics.bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
        new UptimeMetrics().bindTo(registry);
        
        LOGGER.info("Metrics collector initialized for {}:{}", serviceName, serviceId);
    }
    
    public PrometheusMeterRegistry getRegistry() {
        return registry;
    }
    
    public String scrape() {
        return registry.scrape();
    }
    
    // Counter
    public Counter counter(String name, String... tags) {
        return (Counter) meterCache.computeIfAbsent(meterKey(name, tags), k ->
                Counter.builder(name)
                        .tags(tags)
                        .register(registry));
    }
    
    public void incrementCounter(String name, String... tags) {
        counter(name, tags).increment();
    }
    
    // Timer
    public Timer timer(String name, String... tags) {
        return (Timer) meterCache.computeIfAbsent(meterKey(name, tags), k ->
                Timer.builder(name)
                        .tags(tags)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(registry));
    }
    
    public void recordTime(String name, long time, TimeUnit unit, String... tags) {
        timer(name, tags).record(time, unit);
    }
    
    public <T> T recordTimer(String name, Supplier<T> operation, String... tags) {
        return timer(name, tags).record(operation);
    }
    
    // Gauge
    public void gauge(String name, Supplier<Number> supplier, String... tags) {
        meterCache.computeIfAbsent(meterKey(name, tags), k ->
                Gauge.builder(name, supplier)
                        .tags(tags)
                        .register(registry));
    }
    
    // Distribution Summary
    public DistributionSummary summary(String name, String... tags) {
        return (DistributionSummary) meterCache.computeIfAbsent(meterKey(name, tags), k ->
                DistributionSummary.builder(name)
                        .tags(tags)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(registry));
    }
    
    private String meterKey(String name, String... tags) {
        return name + "|" + String.join("|", tags);
    }

    // Custom metrics helpers
    public void recordGrpcCall(String method, String status, long durationMs) {
        timer("grpc.call.duration", "method", method, "status", status)
                .record(durationMs, TimeUnit.MILLISECONDS);
        incrementCounter("grpc.call.total", "method", method, "status", status);
    }
    
    public void recordGauge(String name, Number value, String... tags) {
        gauge(name, () -> value, tags);
    }
    
    public void recordChunkCount(int count) {
        gauge("chunks.loaded", () -> count, "service", serviceName);
    }
    
    public void recordMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        
        gauge("jvm.memory.used.bytes", () -> usedMemory, "service", serviceName);
        gauge("jvm.memory.max.bytes", () -> maxMemory, "service", serviceName);
        gauge("jvm.memory.usage.percent", () -> (usedMemory * 100.0) / maxMemory, "service", serviceName);
    }
    
    public void recordPlayerHandoff(String sourceShard, String targetShard, long durationMs, boolean success) {
        String status = success ? "success" : "failure";
        timer("player.handoff.duration", "source", sourceShard, "target", targetShard, "status", status)
                .record(durationMs, TimeUnit.MILLISECONDS);
        incrementCounter("player.handoff.total", "source", sourceShard, "target", targetShard, "status", status);
    }
    
    public void recordEntitySync(long durationMs, int entityCount) {
        timer("entity.sync.duration").record(durationMs, TimeUnit.MILLISECONDS);
        gauge("entity.sync.count", () -> entityCount, "service", serviceName);
    }
    
    public void recordRedisOperation(String operation, long durationMs, boolean success) {
        String status = success ? "success" : "failure";
        timer("redis.operation.duration", "operation", operation, "status", status)
                .record(durationMs, TimeUnit.MILLISECONDS);
        incrementCounter("redis.operation.total", "operation", operation, "status", status);
    }
    
    public void recordShardHealth(String shardId, boolean healthy, double load) {
        gauge("shard.health", () -> healthy ? 1 : 0, "shard_id", shardId, "service", serviceName);
        gauge("shard.load", () -> load, "shard_id", shardId, "service", serviceName);
    }
    
    public void shutdown() {
        jvmGcMetrics.close();
        jvmHeapPressureMetrics.close();
        registry.close();
        LOGGER.info("Metrics collector shut down for {}:{}", serviceName, serviceId);
    }
}