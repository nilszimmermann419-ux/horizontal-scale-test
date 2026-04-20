package com.shardedmc.shard;

import com.shardedmc.shard.debug.ShardMonitor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.server.ServerTickMonitorEvent;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Performance monitor that tracks TPS, MSPT, entity counts, chunk counts, memory usage,
 * and alerts when performance degrades. Exports metrics to logs.
 */
public class PerformanceMonitor {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitor.class);

    // Configurable thresholds
    private static final double TPS_ALERT_THRESHOLD = 18.0;
    private static final long TPS_ALERT_DURATION_MS = 60000; // 60 seconds
    private static final long METRICS_LOG_INTERVAL_MS = 300000; // 5 minutes
    private static final long METRICS_SAMPLE_INTERVAL_MS = 1000; // 1 second

    private final ShardMonitor shardMonitor;
    private final MemoryMXBean memoryMXBean;

    // Thread-safe state
    private final AtomicReference<Double> currentTps = new AtomicReference<>(20.0);
    private final AtomicReference<Double> currentMspt = new AtomicReference<>(50.0);
    private final AtomicLong tpsAlertStartTime = new AtomicLong(0);
    private final AtomicBoolean tpsAlertActive = new AtomicBoolean(false);
    private final AtomicLong lastMetricsLog = new AtomicLong(0);
    private final Map<String, AtomicLong> entityCounts = new ConcurrentHashMap<>();
    private final AtomicLong loadedChunks = new AtomicLong(0);
    private final AtomicLong activeChunks = new AtomicLong(0);

    public PerformanceMonitor() {
        this.shardMonitor = ShardMonitor.getInstance();
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
    }

    public void register(GlobalEventHandler eventHandler) {
        // Listen for tick events
        eventHandler.addListener(ServerTickMonitorEvent.class, this::onServerTick);

        // Start background monitoring tasks
        startEntityMonitor();
        startChunkMonitor();
        startMetricsLogger();
        startTpsAlertMonitor();

        // Register commands
        registerPerfCommand();

        logger.info("PerformanceMonitor registered");
    }

    private void onServerTick(ServerTickMonitorEvent event) {
        double mspt = event.getTickMonitor().getTickTime();
        double tps = Math.min(20.0, 1000.0 / Math.max(mspt, 1.0));

        currentMspt.set(mspt);
        currentTps.set(tps);

        // Track TPS alert timing
        if (tps < TPS_ALERT_THRESHOLD) {
            long alertStart = tpsAlertStartTime.get();
            if (alertStart == 0) {
                tpsAlertStartTime.set(System.currentTimeMillis());
            }
        } else {
            tpsAlertStartTime.set(0);
            if (tpsAlertActive.compareAndSet(true, false)) {
                logger.info("TPS recovered to {:.2f}. Performance alert cleared.", tps);
                broadcastToOps(Component.text("TPS recovered to ", NamedTextColor.GREEN)
                        .append(Component.text(String.format("%.1f", tps), NamedTextColor.YELLOW)));
            }
        }
    }

    private void startTpsAlertMonitor() {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            long alertStart = tpsAlertStartTime.get();
            if (alertStart > 0 && !tpsAlertActive.get()) {
                long elapsed = System.currentTimeMillis() - alertStart;
                if (elapsed >= TPS_ALERT_DURATION_MS) {
                    double tps = currentTps.get();
                    tpsAlertActive.set(true);
                    logger.error("PERFORMANCE ALERT: TPS has been below {} for {} seconds. Current TPS: {:.2f}",
                            TPS_ALERT_THRESHOLD, elapsed / 1000, tps);
                    broadcastToOps(Component.text("PERFORMANCE ALERT: TPS has been below ", NamedTextColor.RED)
                            .append(Component.text(String.valueOf(TPS_ALERT_THRESHOLD), NamedTextColor.YELLOW))
                            .append(Component.text(" for ", NamedTextColor.RED))
                            .append(Component.text(String.valueOf(elapsed / 1000), NamedTextColor.YELLOW))
                            .append(Component.text(" seconds! Current: ", NamedTextColor.RED))
                            .append(Component.text(String.format("%.1f", tps), NamedTextColor.YELLOW)));
                }
            }
        }).repeat(TaskSchedule.tick(20)).schedule(); // Check every second
    }

    private void startEntityMonitor() {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            Map<String, AtomicLong> counts = new HashMap<>();
            long totalEntities = 0;

            for (var instance : MinecraftServer.getInstanceManager().getInstances()) {
                for (Entity entity : instance.getEntities()) {
                    String typeName = entity.getEntityType().name();
                    counts.computeIfAbsent(typeName, k -> new AtomicLong(0)).incrementAndGet();
                    totalEntities++;
                }
            }

            entityCounts.clear();
            entityCounts.putAll(counts);
        }).repeat(TaskSchedule.tick(20)).schedule(); // Update every second
    }

    private void startChunkMonitor() {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            long loaded = 0;
            long active = 0;

            for (var instance : MinecraftServer.getInstanceManager().getInstances()) {
                var chunks = instance.getChunks();
                loaded += chunks.size();
                // Active chunks = chunks with players nearby (simplified: all loaded for now)
                active += chunks.size();
            }

            loadedChunks.set(loaded);
            activeChunks.set(active);
        }).repeat(TaskSchedule.tick(20)).schedule(); // Update every second
    }

    private void startMetricsLogger() {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            long now = System.currentTimeMillis();
            if (now - lastMetricsLog.get() >= METRICS_LOG_INTERVAL_MS) {
                lastMetricsLog.set(now);
                logMetrics();
            }
        }).repeat(TaskSchedule.tick(100)).schedule(); // Check every 5 seconds
    }

    private void logMetrics() {
        double tps = currentTps.get();
        double mspt = currentMspt.get();
        long loaded = loadedChunks.get();
        long active = activeChunks.get();

        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        long heapUsed = heapUsage.getUsed() / 1024 / 1024;
        long heapMax = heapUsage.getMax() / 1024 / 1024;
        long offHeapUsed = nonHeapUsage.getUsed() / 1024 / 1024;

        int players = MinecraftServer.getConnectionManager().getOnlinePlayers().size();

        logger.info("METRICS - TPS: {:.2f}, MSPT: {:.2f}ms, Players: {}, Chunks: {}/{}, Heap: {}MB/{}MB, OffHeap: {}MB",
                tps, mspt, players, active, loaded, heapUsed, heapMax, offHeapUsed);
    }

    private void registerPerfCommand() {
        Command cmd = new Command("perf");

        cmd.setDefaultExecutor((sender, context) -> {
            double tps = currentTps.get();
            double mspt = currentMspt.get();
            long loaded = loadedChunks.get();
            long active = activeChunks.get();

            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
            MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
            long heapUsed = heapUsage.getUsed() / 1024 / 1024;
            long heapMax = heapUsage.getMax() / 1024 / 1024;
            long heapCommitted = heapUsage.getCommitted() / 1024 / 1024;
            long offHeapUsed = nonHeapUsage.getUsed() / 1024 / 1024;
            long offHeapCommitted = nonHeapUsage.getCommitted() / 1024 / 1024;

            int players = MinecraftServer.getConnectionManager().getOnlinePlayers().size();

            NamedTextColor tpsColor = tps >= 18 ? NamedTextColor.GREEN : (tps >= 15 ? NamedTextColor.YELLOW : NamedTextColor.RED);
            NamedTextColor msptColor = mspt <= 55 ? NamedTextColor.GREEN : (mspt <= 100 ? NamedTextColor.YELLOW : NamedTextColor.RED);

            sender.sendMessage(Component.text("", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("╔════════════════════════════════════════╗", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("║       PERFORMANCE MONITOR              ║", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("╠════════════════════════════════════════╣", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("║  TPS:        ", NamedTextColor.YELLOW)
                    .append(Component.text(String.format("%.2f", tps), tpsColor)));
            sender.sendMessage(Component.text("║  MSPT:       ", NamedTextColor.YELLOW)
                    .append(Component.text(String.format("%.2f ms", mspt), msptColor)));
            sender.sendMessage(Component.text("║  Players:    ", NamedTextColor.YELLOW)
                    .append(Component.text(players, NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("║  Chunks:     ", NamedTextColor.YELLOW)
                    .append(Component.text(active + " / " + loaded, NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("╠════════════════════════════════════════╣", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("║  Heap Memory:", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("║    Used:      ", NamedTextColor.YELLOW)
                    .append(Component.text(heapUsed + " MB", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("║    Committed: ", NamedTextColor.YELLOW)
                    .append(Component.text(heapCommitted + " MB", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("║    Max:       ", NamedTextColor.YELLOW)
                    .append(Component.text(heapMax + " MB", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("╠════════════════════════════════════════╣", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("║  Off-Heap Memory:", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("║    Used:      ", NamedTextColor.YELLOW)
                    .append(Component.text(offHeapUsed + " MB", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("║    Committed: ", NamedTextColor.YELLOW)
                    .append(Component.text(offHeapCommitted + " MB", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("╠════════════════════════════════════════╣", NamedTextColor.GOLD));

            // Top entity types
            sender.sendMessage(Component.text("║  Top Entity Types:", NamedTextColor.YELLOW));
            entityCounts.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                    .limit(5)
                    .forEach(entry -> {
                        sender.sendMessage(Component.text("║    " + entry.getKey() + ": ", NamedTextColor.YELLOW)
                                .append(Component.text(entry.getValue().get(), NamedTextColor.WHITE)));
                    });

            sender.sendMessage(Component.text("╚════════════════════════════════════════╝", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("", NamedTextColor.GOLD));
        });

        MinecraftServer.getCommandManager().register(cmd);
    }

    private void broadcastToOps(Component message) {
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    // === Public API ===

    public double getCurrentTps() {
        return currentTps.get();
    }

    public double getCurrentMspt() {
        return currentMspt.get();
    }

    public long getLoadedChunks() {
        return loadedChunks.get();
    }

    public long getActiveChunks() {
        return activeChunks.get();
    }

    public Map<String, Long> getEntityCounts() {
        Map<String, Long> result = new HashMap<>();
        entityCounts.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    public boolean isTpsAlertActive() {
        return tpsAlertActive.get();
    }
}
