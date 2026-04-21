package com.shardedmc.shard.metrics;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.exporter.PushGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ShardMetrics {
    private static final Logger logger = LoggerFactory.getLogger(ShardMetrics.class);

    private final String shardId;
    private final CollectorRegistry registry;
    private final PushGateway pushGateway;
    private final boolean pushEnabled;
    private final ScheduledExecutorService scheduler;

    private final Gauge tpsGauge;
    private final Histogram chunkLoadTimeHistogram;
    private final Gauge playerCountGauge;
    private final Gauge entityCountGauge;
    private final Gauge eventPublishRateGauge;
    private final Gauge memoryUsageGauge;

    private final AtomicInteger eventCount = new AtomicInteger(0);
    private volatile double currentTps = 20.0;

    public ShardMetrics(String shardId) {
        this(shardId, null);
    }

    public ShardMetrics(String shardId, String pushGatewayAddress) {
        this.shardId = shardId;
        this.registry = new CollectorRegistry();
        this.pushEnabled = pushGatewayAddress != null && !pushGatewayAddress.isEmpty();
        this.pushGateway = pushEnabled ? new PushGateway(pushGatewayAddress) : null;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "shard-metrics");
            t.setDaemon(true);
            return t;
        });

        this.tpsGauge = Gauge.build()
                .name("shard_tps")
                .help("Current ticks per second")
                .labelNames("shard_id")
                .register(registry);

        this.chunkLoadTimeHistogram = Histogram.build()
                .name("shard_chunk_load_time_seconds")
                .help("Chunk load time in seconds")
                .labelNames("shard_id")
                .buckets(0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0)
                .register(registry);

        this.playerCountGauge = Gauge.build()
                .name("shard_player_count")
                .help("Number of players on this shard")
                .labelNames("shard_id")
                .register(registry);

        this.entityCountGauge = Gauge.build()
                .name("shard_entity_count")
                .help("Number of entities on this shard")
                .labelNames("shard_id")
                .register(registry);

        this.eventPublishRateGauge = Gauge.build()
                .name("shard_event_publish_rate")
                .help("Events published per second")
                .labelNames("shard_id")
                .register(registry);

        this.memoryUsageGauge = Gauge.build()
                .name("shard_memory_usage_bytes")
                .help("Current memory usage in bytes")
                .labelNames("shard_id", "type")
                .register(registry);

        startEventRateCalculator();
    }

    /**
     * Records the current TPS (ticks per second)
     */
    public void recordTPS(double tps) {
        this.currentTps = tps;
        tpsGauge.labels(shardId).set(tps);
    }

    /**
     * Records a chunk load time observation
     */
    public void recordChunkLoadTime(long millis) {
        chunkLoadTimeHistogram.labels(shardId).observe(millis / 1000.0);
    }

    /**
     * Sets the current player count
     */
    public void setPlayerCount(int count) {
        playerCountGauge.labels(shardId).set(count);
    }

    /**
     * Sets the current entity count
     */
    public void setEntityCount(int count) {
        entityCountGauge.labels(shardId).set(count);
    }

    /**
     * Records an event being published
     */
    public void recordEventPublished() {
        eventCount.incrementAndGet();
    }

    /**
     * Updates memory usage metrics from JVM
     */
    public void updateMemoryUsage() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();

        memoryUsageGauge.labels(shardId, "heap").set(heapUsage.getUsed());
        memoryUsageGauge.labels(shardId, "heap_max").set(heapUsage.getMax());
        memoryUsageGauge.labels(shardId, "non_heap").set(nonHeapUsage.getUsed());
    }

    /**
     * Publishes all metrics to logs and optionally pushes to Prometheus PushGateway
     */
    public void publishMetrics() {
        updateMemoryUsage();

        logger.info("Shard metrics: shard_id={}, tps={}, players={}, entities={}, events/sec={}",
                shardId,
                String.format("%.2f", currentTps),
                (int) playerCountGauge.labels(shardId).get(),
                (int) entityCountGauge.labels(shardId).get(),
                (int) eventPublishRateGauge.labels(shardId).get()
        );

        if (pushEnabled && pushGateway != null) {
            try {
                pushGateway.pushAdd(registry, "shardedmc_shard");
            } catch (Exception e) {
                logger.warn("Failed to push metrics to Prometheus gateway: {}", e.getMessage());
            }
        }
    }

    private void startEventRateCalculator() {
        scheduler.scheduleAtFixedRate(() -> {
            int events = eventCount.getAndSet(0);
            eventPublishRateGauge.labels(shardId).set(events);
        }, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * Stops the metrics scheduler
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}
