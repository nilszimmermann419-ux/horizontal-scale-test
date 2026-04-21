package com.shardedmc.shard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Detects and mitigates redstone lag machines by tracking updates per chunk.
 *
 * Features:
 * - Tracks redstone updates per chunk per second
 * - Alerts when chunk exceeds threshold (default: 1000 updates/sec)
 * - Limits observer/piston counts per chunk (default: 256)
 * - Auto-disables redstone in lagging chunks after warning
 * - Logs repeat offenders for admin review
 */
public class RedstoneLagDetector {
    private static final Logger logger = LoggerFactory.getLogger(RedstoneLagDetector.class);

    private static final int DEFAULT_MAX_UPDATES_PER_SECOND = 1000;
    private static final int DEFAULT_OBSERVER_PISTON_LIMIT = 256;
    private static final int WARNING_THRESHOLD_PERCENT = 80; // Warn at 80% of limit
    private static final int AUTO_DISABLE_THRESHOLD = 3; // Disable after 3 warnings
    private static final Duration ALERT_COOLDOWN = Duration.ofSeconds(30);
    private static final Duration RESET_INTERVAL = Duration.ofSeconds(1);

    private final boolean enabled;
    private final int maxUpdatesPerSecond;
    private final int observerPistonLimit;

    // Update tracking: chunk key -> update count for current second
    private final Map<String, AtomicInteger> chunkUpdateCounts = new ConcurrentHashMap<>();

    // Observer/piston counts per chunk
    private final Map<String, AtomicInteger> chunkComponentCounts = new ConcurrentHashMap<>();

    // Warning counts per chunk (for auto-disable)
    private final Map<String, AtomicInteger> chunkWarningCounts = new ConcurrentHashMap<>();

    // Disabled chunks (redstone disabled)
    private final Set<String> disabledChunks = ConcurrentHashMap.newKeySet();

    // Last alert time per chunk
    private final Map<String, AtomicLong> lastAlertTimes = new ConcurrentHashMap<>();

    // Statistics
    private final AtomicInteger totalAlerts = new AtomicInteger(0);
    private final AtomicInteger totalDisabled = new AtomicInteger(0);
    private final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());

    public RedstoneLagDetector() {
        this.enabled = Boolean.parseBoolean(System.getenv().getOrDefault("REDSTONE_LAG_DETECTOR_ENABLED", "true"));
        this.maxUpdatesPerSecond = Integer.parseInt(System.getenv().getOrDefault("REDSTONE_MAX_UPDATES_PER_SECOND", String.valueOf(DEFAULT_MAX_UPDATES_PER_SECOND)));
        this.observerPistonLimit = Integer.parseInt(System.getenv().getOrDefault("REDSTONE_OBSERVER_PISTON_LIMIT", String.valueOf(DEFAULT_OBSERVER_PISTON_LIMIT)));
    }

    public RedstoneLagDetector(boolean enabled, int maxUpdatesPerSecond, int observerPistonLimit) {
        this.enabled = enabled;
        this.maxUpdatesPerSecond = maxUpdatesPerSecond;
        this.observerPistonLimit = observerPistonLimit;
    }

    public void register(GlobalEventHandler eventHandler) {
        if (!enabled) {
            logger.info("RedstoneLagDetector is disabled");
            return;
        }

        // Track block placements (for observer/piston counting)
        eventHandler.addListener(PlayerBlockPlaceEvent.class, this::onBlockPlace);
        eventHandler.addListener(PlayerBlockBreakEvent.class, this::onBlockBreak);

        // Schedule periodic reset and check
        MinecraftServer.getSchedulerManager().buildTask(this::tick)
                .repeat(RESET_INTERVAL)
                .schedule();

        logger.info("RedstoneLagDetector registered (max {} updates/sec, {} observer/piston limit per chunk)",
                maxUpdatesPerSecond, observerPistonLimit);
    }

    /**
     * Track a redstone update at a position.
     * Called by redstone systems when they update blocks.
     */
    public void trackRedstoneUpdate(Point pos) {
        if (!enabled) return;

        String chunkKey = getChunkKey(pos);

        // Don't count if chunk is already disabled
        if (disabledChunks.contains(chunkKey)) {
            return;
        }

        chunkUpdateCounts.computeIfAbsent(chunkKey, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * Check if redstone is disabled in a chunk.
     */
    public boolean isChunkDisabled(Point pos) {
        return disabledChunks.contains(getChunkKey(pos));
    }

    /**
     * Check if redstone is disabled for a chunk key.
     */
    public boolean isChunkDisabled(String chunkKey) {
        return disabledChunks.contains(chunkKey);
    }

    /**
     * Enable redstone in a previously disabled chunk.
     */
    public void enableChunk(String chunkKey) {
        disabledChunks.remove(chunkKey);
        chunkWarningCounts.remove(chunkKey);
        logger.info("Redstone re-enabled in chunk {}", chunkKey);
    }

    /**
     * Get all currently disabled chunks.
     */
    public Set<String> getDisabledChunks() {
        return Set.copyOf(disabledChunks);
    }

    private void onBlockPlace(PlayerBlockPlaceEvent event) {
        if (!enabled) return;

        Block block = event.getBlock();
        if (!isTrackedComponent(block)) {
            return;
        }

        String chunkKey = getChunkKey(event.getBlockPosition());
        int count = chunkComponentCounts.computeIfAbsent(chunkKey, k -> new AtomicInteger(0)).incrementAndGet();

        // Check if limit exceeded
        if (count > observerPistonLimit) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text(
                    "Redstone component limit reached in this chunk (" + observerPistonLimit + ")!"
            ).color(NamedTextColor.RED));
            logger.warn("Player {} tried to place {} but chunk {} has reached the observer/piston limit",
                    event.getPlayer().getUsername(), block.name(), chunkKey);
        }
    }

    private void onBlockBreak(PlayerBlockBreakEvent event) {
        if (!enabled) return;

        Block block = event.getBlock();
        if (!isTrackedComponent(block)) {
            return;
        }

        String chunkKey = getChunkKey(event.getBlockPosition());
        AtomicInteger count = chunkComponentCounts.get(chunkKey);
        if (count != null) {
            count.decrementAndGet();
            if (count.get() <= 0) {
                chunkComponentCounts.remove(chunkKey);
            }
        }
    }

    /**
     * Periodic tick: check for lagging chunks and reset counters.
     */
    private void tick() {
        if (!enabled) return;

        long now = System.currentTimeMillis();

        // Check all chunks for exceeding limits
        for (Map.Entry<String, AtomicInteger> entry : chunkUpdateCounts.entrySet()) {
            String chunkKey = entry.getKey();
            int count = entry.getValue().get();

            if (count > maxUpdatesPerSecond) {
                handleLagChunk(chunkKey, count);
            } else if (count > maxUpdatesPerSecond * WARNING_THRESHOLD_PERCENT / 100) {
                // Warn at 80% threshold
                handleWarningChunk(chunkKey, count);
            }
        }

        // Reset counters every second
        if (now - lastResetTime.get() >= 1000) {
            chunkUpdateCounts.clear();
            lastResetTime.set(now);
        }
    }

    private void handleLagChunk(String chunkKey, int updateCount) {
        AtomicLong lastAlert = lastAlertTimes.get(chunkKey);
        long now = System.currentTimeMillis();

        // Check cooldown
        if (lastAlert != null && now - lastAlert.get() < ALERT_COOLDOWN.toMillis()) {
            return;
        }

        // Update last alert time
        lastAlertTimes.computeIfAbsent(chunkKey, k -> new AtomicLong(0)).set(now);

        // Increment warning count
        AtomicInteger warnings = chunkWarningCounts.computeIfAbsent(chunkKey, k -> new AtomicInteger(0));
        int warningCount = warnings.incrementAndGet();

        totalAlerts.incrementAndGet();

        logger.warn("LAG DETECTED: Chunk {} has {} redstone updates/sec (limit: {})",
                chunkKey, updateCount, maxUpdatesPerSecond);

        // Broadcast to online ops/admins
        broadcastAlert(chunkKey, updateCount, warningCount);

        // Auto-disable after threshold
        if (warningCount >= AUTO_DISABLE_THRESHOLD) {
            disabledChunks.add(chunkKey);
            totalDisabled.incrementAndGet();

            logger.error("AUTO-DISABLED redstone in chunk {} after {} warnings ({} updates/sec)",
                    chunkKey, warningCount, updateCount);

            broadcastDisable(chunkKey);
        }
    }

    private void handleWarningChunk(String chunkKey, int updateCount) {
        AtomicLong lastAlert = lastAlertTimes.get(chunkKey);
        long now = System.currentTimeMillis();

        // Only warn every 60 seconds for warning-level activity
        if (lastAlert != null && now - lastAlert.get() < 60000) {
            return;
        }

        lastAlertTimes.computeIfAbsent(chunkKey, k -> new AtomicLong(0)).set(now);

        logger.warn("WARNING: Chunk {} has {} redstone updates/sec (approaching limit of {})",
                chunkKey, updateCount, maxUpdatesPerSecond);
    }

    private void broadcastAlert(String chunkKey, int updateCount, int warningCount) {
        Component message = Component.text("[Lag Detector] ")
                .color(NamedTextColor.YELLOW)
                .append(Component.text("Chunk " + chunkKey + " has " + updateCount + " redstone updates/sec! "))
                .append(Component.text("Warning " + warningCount + "/" + AUTO_DISABLE_THRESHOLD)
                        .color(NamedTextColor.RED));

        // Send to all online players with operator permissions
        for (var player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (player.getPermissionLevel() >= 4) {
                player.sendMessage(message);
            }
        }
    }

    private void broadcastDisable(String chunkKey) {
        Component message = Component.text("[Lag Detector] ")
                .color(NamedTextColor.RED)
                .append(Component.text("Redstone AUTO-DISABLED in chunk " + chunkKey +
                        " due to excessive lag. Contact an admin to re-enable."));

        // Send to all online players
        for (var player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    /**
     * Check if a block is a tracked redstone component (observer or piston).
     */
    private boolean isTrackedComponent(Block block) {
        String name = block.name();
        return name.equals("minecraft:observer") ||
                name.equals("minecraft:piston") ||
                name.equals("minecraft:sticky_piston") ||
                name.equals("minecraft:dispenser") ||
                name.equals("minecraft:dropper");
    }

    /**
     * Check if a block is a redstone component that triggers updates.
     */
    private boolean isRedstoneComponent(Block block) {
        String name = block.name();
        return name.contains("redstone") ||
                name.contains("repeater") ||
                name.contains("comparator") ||
                name.contains("observer") ||
                name.contains("piston") ||
                name.contains("dispenser") ||
                name.contains("dropper") ||
                name.contains("door") ||
                name.contains("trapdoor") ||
                name.contains("gate") ||
                name.contains("note_block") ||
                name.contains("hopper") ||
                name.contains("daylight_detector") ||
                name.contains("lever") ||
                name.contains("button") ||
                name.contains("pressure_plate") ||
                name.contains("detector_rail") ||
                name.contains("trapped_chest");
    }

    private String getChunkKey(Point pos) {
        int chunkX = pos.blockX() >> 4;
        int chunkZ = pos.blockZ() >> 4;
        return chunkX + "," + chunkZ;
    }

    /**
     * Get statistics for monitoring.
     */
    public Map<String, Object> getStatistics() {
        return Map.of(
                "totalAlerts", totalAlerts.get(),
                "totalDisabledChunks", totalDisabled.get(),
                "currentlyDisabledChunks", disabledChunks.size(),
                "activeChunkCounters", chunkUpdateCounts.size()
        );
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxUpdatesPerSecond() {
        return maxUpdatesPerSecond;
    }

    public int getObserverPistonLimit() {
        return observerPistonLimit;
    }
}
