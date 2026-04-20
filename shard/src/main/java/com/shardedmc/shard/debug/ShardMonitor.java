package com.shardedmc.shard.debug;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Monitors shard performance metrics including TPS, memory usage, and tick times.
 * Provides performance alerts when TPS drops below configurable thresholds.
 */
public class ShardMonitor {
    private static final Logger logger = LoggerFactory.getLogger(ShardMonitor.class);
    private static final int TPS_WINDOW_SIZE = 20; // 1 second of ticks
    private static final double TPS_WARNING_THRESHOLD = 18.0;
    private static final double TPS_CRITICAL_THRESHOLD = 15.0;
    private static final long TICK_NANOS = 50_000_000L; // 50ms in nanos

    // Singleton instance
    private static ShardMonitor instance;

    // Tick tracking
    private final Deque<Long> tickTimes = new ArrayDeque<>(TPS_WINDOW_SIZE);
    private final AtomicLong lastTickTime = new AtomicLong(System.nanoTime());
    private final AtomicLong tickCount = new AtomicLong(0);
    private final AtomicReference<Double> currentTps = new AtomicReference<>(20.0);
    private final AtomicReference<Double> averageTickTime = new AtomicReference<>(50.0); // ms

    // Memory tracking
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final AtomicLong lastGcCount = new AtomicLong(0);
    private final AtomicLong lastGcTime = new AtomicLong(0);

    // Performance alerts
    private long lastWarningTime = 0;
    private boolean warningActive = false;

    // Timings
    private final Deque<Long> tickTimings = new ArrayDeque<>(100);

    public static synchronized ShardMonitor getInstance() {
        if (instance == null) {
            instance = new ShardMonitor();
        }
        return instance;
    }

    private ShardMonitor() {
        startTickMonitor();
        startMemoryMonitor();
    }

    /**
     * Start monitoring server ticks to calculate TPS
     */
    private void startTickMonitor() {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            long now = System.nanoTime();
            long lastTick = lastTickTime.getAndSet(now);
            long tickDuration = now - lastTick;

            tickCount.incrementAndGet();

            // Store tick time in ms
            double tickTimeMs = tickDuration / 1_000_000.0;
            averageTickTime.set(tickTimeMs);

            // Add to rolling window
            synchronized (tickTimes) {
                tickTimes.addLast(tickDuration);
                while (tickTimes.size() > TPS_WINDOW_SIZE) {
                    tickTimes.removeFirst();
                }

                // Calculate TPS from rolling window
                if (tickTimes.size() >= 10) {
                    long totalNanos = 0;
                    for (long t : tickTimes) {
                        totalNanos += t;
                    }
                    double avgTickNanos = (double) totalNanos / tickTimes.size();
                    double tps = Math.min(20.0, TICK_NANOS / avgTickNanos * 20.0);
                    currentTps.set(tps);

                    // Check for performance degradation
                    checkPerformanceAlert(tps);
                }
            }

            // Store for timings
            synchronized (tickTimings) {
                tickTimings.addLast(tickDuration);
                while (tickTimings.size() > 100) {
                    tickTimings.removeFirst();
                }
            }
        }).repeat(TaskSchedule.tick(1)).schedule();
    }

    /**
     * Start background memory monitoring
     */
    private void startMemoryMonitor() {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            // Trigger warning if TPS stays low
            double tps = currentTps.get();
            if (tps < TPS_CRITICAL_THRESHOLD) {
                logger.warn("CRITICAL: TPS dropped to {:.2f}. Server performance severely degraded!", tps);
            }
        }).repeat(TaskSchedule.tick(20)).schedule(); // Check every second
    }

    /**
     * Check and emit performance alerts
     */
    private void checkPerformanceAlert(double tps) {
        long now = System.currentTimeMillis();

        if (tps < TPS_WARNING_THRESHOLD) {
            if (!warningActive || (now - lastWarningTime) > 30000) { // 30 second cooldown
                warningActive = true;
                lastWarningTime = now;

                if (tps < TPS_CRITICAL_THRESHOLD) {
                    logger.error("PERFORMANCE ALERT: TPS critical at {:.2f} (threshold: {:.2f})",
                            tps, TPS_CRITICAL_THRESHOLD);
                    broadcastAlert(Component.text("⚠ CRITICAL: Server TPS dropped to ", NamedTextColor.RED)
                            .append(Component.text(String.format("%.1f", tps), NamedTextColor.YELLOW))
                            .append(Component.text("! Performance severely degraded.", NamedTextColor.RED)));
                } else {
                    logger.warn("PERFORMANCE ALERT: TPS low at {:.2f} (threshold: {:.2f})",
                            tps, TPS_WARNING_THRESHOLD);
                    broadcastAlert(Component.text("⚠ WARNING: Server TPS is ", NamedTextColor.YELLOW)
                            .append(Component.text(String.format("%.1f", tps), NamedTextColor.RED))
                            .append(Component.text(". Performance may be degraded.", NamedTextColor.YELLOW)));
                }
            }
        } else {
            if (warningActive) {
                warningActive = false;
                logger.info("PERFORMANCE RECOVERY: TPS back to normal at {:.2f}", tps);
                broadcastAlert(Component.text("✓ Server performance recovered. TPS: ", NamedTextColor.GREEN)
                        .append(Component.text(String.format("%.1f", tps), NamedTextColor.YELLOW)));
            }
        }
    }

    /**
     * Broadcast a performance alert to all online operators
     */
    private void broadcastAlert(Component message) {
        for (var player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            // Broadcast to all players (operators and above in permission level)
            player.sendMessage(message);
        }
    }

    // === Public API ===

    /**
     * Get current TPS (rolling average)
     */
    public double getCurrentTps() {
        return currentTps.get();
    }

    /**
     * Get current tick time in milliseconds
     */
    public double getCurrentTickTime() {
        return averageTickTime.get();
    }

    /**
     * Get total number of ticks since start
     */
    public long getTotalTicks() {
        return tickCount.get();
    }

    /**
     * Get tick time statistics
     */
    public TickStats getTickStats() {
        synchronized (tickTimings) {
            if (tickTimings.isEmpty()) {
                return new TickStats(0, 0, 0, 0);
            }

            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            long sum = 0;

            for (long t : tickTimings) {
                min = Math.min(min, t);
                max = Math.max(max, t);
                sum += t;
            }

            double avg = sum / (double) tickTimings.size() / 1_000_000.0; // Convert to ms
            double minMs = min / 1_000_000.0;
            double maxMs = max / 1_000_000.0;

            // Calculate percentiles
            double[] sorted = tickTimings.stream()
                    .mapToDouble(t -> t / 1_000_000.0)
                    .sorted()
                    .toArray();
            double p95 = sorted[(int) (sorted.length * 0.95)];
            double p99 = sorted[(int) (sorted.length * 0.99)];

            return new TickStats(avg, minMs, maxMs, sorted.length, p95, p99);
        }
    }

    /**
     * Get memory usage statistics
     */
    public MemoryStats getMemoryStats() {
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();

        long heapUsed = heapUsage.getUsed();
        long heapCommitted = heapUsage.getCommitted();
        long heapMax = heapUsage.getMax();
        long nonHeapUsed = nonHeapUsage.getUsed();
        long nonHeapCommitted = nonHeapUsage.getCommitted();

        // Calculate GC statistics
        long totalGcCount = 0;
        long totalGcTime = 0;
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gcBean.getCollectionCount();
            long time = gcBean.getCollectionTime();
            if (count >= 0) totalGcCount += count;
            if (time >= 0) totalGcTime += time;
        }

        long gcCountDelta = totalGcCount - lastGcCount.getAndSet(totalGcCount);
        long gcTimeDelta = totalGcTime - lastGcTime.getAndSet(totalGcTime);

        return new MemoryStats(heapUsed, heapCommitted, heapMax, nonHeapUsed, nonHeapCommitted,
                totalGcCount, totalGcTime, gcCountDelta, gcTimeDelta);
    }

    /**
     * Trigger garbage collection and return before/after stats
     */
    public MemoryStats triggerGc() {
        MemoryStats before = getMemoryStats();
        System.gc();
        // Give GC a moment to complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        MemoryStats after = getMemoryStats();
        return new MemoryStats(
                before.heapUsed - after.heapUsed,
                after.heapCommitted,
                after.heapMax,
                after.nonHeapUsed,
                after.nonHeapCommitted,
                after.totalGcCount,
                after.totalGcTime,
                after.gcCountDelta,
                after.gcTimeDelta
        );
    }

    // === Record classes ===

    public record TickStats(double averageMs, double minMs, double maxMs, int sampleSize,
                            double p95Ms, double p99Ms) {
        public TickStats(double averageMs, double minMs, double maxMs, int sampleSize) {
            this(averageMs, minMs, maxMs, sampleSize, 0, 0);
        }
    }

    public record MemoryStats(long heapUsed, long heapCommitted, long heapMax,
                              long nonHeapUsed, long nonHeapCommitted,
                              long totalGcCount, long totalGcTime,
                              long gcCountDelta, long gcTimeDelta) {
    }
}
