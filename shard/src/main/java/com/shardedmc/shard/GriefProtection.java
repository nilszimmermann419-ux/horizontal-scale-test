package com.shardedmc.shard;

import com.shardedmc.shared.RedisClient;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grief protection and rollback system.
 * Logs all block changes to Redis with 7-day retention.
 * Provides /rollback and /inspect commands for admins.
 */
public class GriefProtection {
    private static final Logger logger = LoggerFactory.getLogger(GriefProtection.class);

    // Configuration
    private final long retentionDays;
    private final int maxRollbackRadius;
    private final int maxRollbackMinutes;

    // Redis client for async logging
    private final RedisClient redis;

    // Redis key prefix
    private static final String BLOCK_LOG_PREFIX = "blocklog:";
    private static final String BLOCK_LOG_INDEX = "blocklog:index";

    // Date formatter for display
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    // Pending async operations counter for monitoring
    private volatile long pendingOperations = 0;

    // Inspect mode tracking: player UUID -> boolean
    private final Set<UUID> inspectModePlayers = ConcurrentHashMap.newKeySet();

    public GriefProtection(RedisClient redis) {
        this(redis, 7, 100, 1440); // 7 days, 100 blocks, 24 hours max
    }

    public GriefProtection(RedisClient redis, long retentionDays, int maxRollbackRadius, int maxRollbackMinutes) {
        this.redis = redis;
        this.retentionDays = retentionDays;
        this.maxRollbackRadius = maxRollbackRadius;
        this.maxRollbackMinutes = maxRollbackMinutes;
    }

    public void register(EventNode<Event> eventNode) {
        // Block break logging
        eventNode.addListener(PlayerBlockBreakEvent.class, this::onBlockBreak);

        // Block place logging
        eventNode.addListener(PlayerBlockPlaceEvent.class, this::onBlockPlace);

        // Register commands
        registerRollbackCommand();
        registerInspectCommand();
        registerInspectToggleCommand();

        // Start retention cleanup task
        startRetentionCleanup();

        logger.info("GriefProtection registered (retention={}d, maxRadius={}, maxRollback={}min)",
                retentionDays, maxRollbackRadius, maxRollbackMinutes);
    }

    private void onBlockBreak(PlayerBlockBreakEvent event) {
        Player player = event.getPlayer();
        Point pos = event.getBlockPosition();
        Block block = event.getBlock();

        BlockChangeRecord record = new BlockChangeRecord(
                player.getUuid().toString(),
                player.getUsername(),
                System.currentTimeMillis(),
                block.name(),
                Block.AIR.name(),
                "break",
                pos.blockX(), pos.blockY(), pos.blockZ()
        );

        logBlockChangeAsync(record);
    }

    private void onBlockPlace(PlayerBlockPlaceEvent event) {
        Player player = event.getPlayer();
        Point pos = event.getBlockPosition();
        Block block = event.getBlock();
        Block oldBlock = event.getInstance().getBlock(pos);

        BlockChangeRecord record = new BlockChangeRecord(
                player.getUuid().toString(),
                player.getUsername(),
                System.currentTimeMillis(),
                oldBlock.name(),
                block.name(),
                "place",
                pos.blockX(), pos.blockY(), pos.blockZ()
        );

        logBlockChangeAsync(record);
    }

    private void logBlockChangeAsync(BlockChangeRecord record) {
        pendingOperations++;

        String blockKey = getBlockKey(record.x, record.y, record.z);
        String serialized = record.serialize();

        // Use async Redis operations
        CompletableFuture.runAsync(() -> {
            try {
                // Add to block-specific log with TTL
                redis.setexAsync(blockKey, retentionDays * 86400, serialized)
                        .thenRun(() -> {
                            // Also add to global index for rollback queries
                            String indexKey = BLOCK_LOG_INDEX + ":" + record.x + ":" + record.z;
                            redis.setexAsync(indexKey, retentionDays * 86400, String.valueOf(record.time))
                                    .thenRun(() -> pendingOperations--)
                                    .exceptionally(ex -> {
                                        pendingOperations--;
                                        return null;
                                    });
                        })
                        .exceptionally(ex -> {
                            pendingOperations--;
                            logger.error("Failed to log block change", ex);
                            return null;
                        });
            } catch (Exception e) {
                pendingOperations--;
                logger.error("Error in async block logging", e);
            }
        });
    }

    private String getBlockKey(int x, int y, int z) {
        return BLOCK_LOG_PREFIX + x + ":" + y + ":" + z;
    }

    private void registerRollbackCommand() {
        Command cmd = new Command("rollback");

        var minutesArg = ArgumentType.Integer("minutes").min(1).max(maxRollbackMinutes);
        var radiusArg = ArgumentType.Integer("radius").min(1).max(maxRollbackRadius);

        cmd.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can use this command", NamedTextColor.RED));
                return;
            }

            int minutes = context.get(minutesArg);
            int radius = context.get(radiusArg);

            performRollback(player, minutes, radius);
        }, minutesArg, radiusArg);

        cmd.setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("Usage: /rollback <minutes> <radius>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Example: /rollback 30 20", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("Rolls back block changes in the last 30 minutes within 20 blocks", NamedTextColor.GRAY));
        });

        MinecraftServer.getCommandManager().register(cmd);
    }

    private void registerInspectCommand() {
        Command cmd = new Command("inspect");

        var xArg = ArgumentType.Integer("x");
        var yArg = ArgumentType.Integer("y");
        var zArg = ArgumentType.Integer("z");

        cmd.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can use this command", NamedTextColor.RED));
                return;
            }

            int x = context.get(xArg);
            int y = context.get(yArg);
            int z = context.get(zArg);

            inspectBlock(player, x, y, z);
        }, xArg, yArg, zArg);

        // Inspect the block the player is looking at
        cmd.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Usage: /inspect <x> <y> <z>", NamedTextColor.RED));
                return;
            }

            // Get the block the player is looking at (simplified - just use player position)
            Pos pos = player.getPosition();
            inspectBlock(player, pos.blockX(), pos.blockY(), pos.blockZ());
        });

        MinecraftServer.getCommandManager().register(cmd);
    }

    private void registerInspectToggleCommand() {
        Command cmd = new Command("inspecttoggle");

        cmd.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can use this command", NamedTextColor.RED));
                return;
            }

            UUID uuid = player.getUuid();
            boolean enabled = !inspectModePlayers.contains(uuid);

            if (enabled) {
                inspectModePlayers.add(uuid);
                player.sendMessage(Component.text("Inspect mode enabled! Right-click blocks to see their history.", NamedTextColor.GREEN));
            } else {
                inspectModePlayers.remove(uuid);
                player.sendMessage(Component.text("Inspect mode disabled.", NamedTextColor.YELLOW));
            }
        });

        MinecraftServer.getCommandManager().register(cmd);
    }

    private void inspectBlock(Player player, int x, int y, int z) {
        String blockKey = getBlockKey(x, y, z);

        CompletableFuture.supplyAsync(() -> redis.get(blockKey))
                .thenAccept(serialized -> {
                    if (serialized == null || serialized.isEmpty()) {
                        player.sendMessage(Component.text("No block history found at " + x + ", " + y + ", " + z, NamedTextColor.YELLOW));
                        return;
                    }

                    BlockChangeRecord record = BlockChangeRecord.deserialize(serialized);
                    if (record == null) {
                        player.sendMessage(Component.text("Invalid block history data", NamedTextColor.RED));
                        return;
                    }

                    player.sendMessage(Component.text("", NamedTextColor.GOLD));
                    player.sendMessage(Component.text("╔════════════════════════════════════════╗", NamedTextColor.GOLD));
                    player.sendMessage(Component.text("║         BLOCK HISTORY                  ║", NamedTextColor.GOLD));
                    player.sendMessage(Component.text("╠════════════════════════════════════════╣", NamedTextColor.GOLD));
                    player.sendMessage(Component.text("║  Position:  " + x + ", " + y + ", " + z, NamedTextColor.YELLOW));
                    player.sendMessage(Component.text("║  Player:    " + record.playerName, NamedTextColor.YELLOW));
                    player.sendMessage(Component.text("║  Action:    " + record.action.toUpperCase(), NamedTextColor.YELLOW));
                    player.sendMessage(Component.text("║  Time:      " + DATE_FORMATTER.format(Instant.ofEpochMilli(record.time)), NamedTextColor.YELLOW));
                    player.sendMessage(Component.text("║  From:      " + record.fromBlock, NamedTextColor.YELLOW));
                    player.sendMessage(Component.text("║  To:        " + record.toBlock, NamedTextColor.YELLOW));
                    player.sendMessage(Component.text("╚════════════════════════════════════════╝", NamedTextColor.GOLD));
                })
                .exceptionally(ex -> {
                    player.sendMessage(Component.text("Error retrieving block history: " + ex.getMessage(), NamedTextColor.RED));
                    logger.error("Error inspecting block at {},{},{}", x, y, z, ex);
                    return null;
                });
    }

    private void performRollback(Player player, int minutes, int radius) {
        long cutoffTime = System.currentTimeMillis() - (minutes * 60000L);
        Pos center = player.getPosition();
        Instance instance = player.getInstance();

        player.sendMessage(Component.text("Starting rollback...", NamedTextColor.YELLOW));

        // Collect all block keys first to batch Redis lookups
        List<String> blockKeys = new ArrayList<>();
        List<int[]> blockCoords = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = center.blockX() + dx;
                    int y = center.blockY() + dy;
                    int z = center.blockZ() + dz;
                    blockKeys.add(getBlockKey(x, y, z));
                    blockCoords.add(new int[]{x, y, z});
                }
            }
        }

        // Batch fetch all block history asynchronously
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (String key : blockKeys) {
            futures.add(redis.getAsync(key));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRunAsync(() -> {
            int rolledBack = 0;
            int failed = 0;

            for (int i = 0; i < futures.size(); i++) {
                String serialized;
                try {
                    serialized = futures.get(i).get();
                } catch (Exception e) {
                    failed++;
                    continue;
                }

                if (serialized == null || serialized.isEmpty()) continue;

                BlockChangeRecord record = BlockChangeRecord.deserialize(serialized);
                if (record == null) continue;

                // Check if within time window
                if (record.time < cutoffTime) continue;

                // Perform rollback
                try {
                    Block rollbackBlock = Block.fromKey(record.fromBlock);
                    if (rollbackBlock != null) {
                        int[] coords = blockCoords.get(i);
                        final int fx = coords[0], fy = coords[1], fz = coords[2];
                        final Block fBlock = rollbackBlock;
                        MinecraftServer.getSchedulerManager().buildTask(() -> {
                            instance.setBlock(fx, fy, fz, fBlock);
                        }).schedule();
                        rolledBack++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    failed++;
                    logger.error("Error rolling back block", e);
                }
            }

            final int finalRolledBack = rolledBack;
            final int finalFailed = failed;

            // Send results to player on main thread
            MinecraftServer.getSchedulerManager().buildTask(() -> {
                player.sendMessage(Component.text("", NamedTextColor.GOLD));
                player.sendMessage(Component.text("╔════════════════════════════════════════╗", NamedTextColor.GOLD));
                player.sendMessage(Component.text("║         ROLLBACK COMPLETE              ║", NamedTextColor.GOLD));
                player.sendMessage(Component.text("╠════════════════════════════════════════╣", NamedTextColor.GOLD));
                player.sendMessage(Component.text("║  Radius:     " + radius + " blocks", NamedTextColor.YELLOW));
                player.sendMessage(Component.text("║  Time:       " + minutes + " minutes", NamedTextColor.YELLOW));
                player.sendMessage(Component.text("║  Rolled back: " + finalRolledBack, NamedTextColor.GREEN));
                if (finalFailed > 0) {
                    player.sendMessage(Component.text("║  Failed:     " + finalFailed, NamedTextColor.RED));
                }
                player.sendMessage(Component.text("╚════════════════════════════════════════╝", NamedTextColor.GOLD));
            }).schedule();

            logger.info("Rollback completed by {}: {} blocks rolled back, {} failed (radius={}, minutes={})",
                    player.getUsername(), rolledBack, failed, radius, minutes);
        });
    }

    private void startRetentionCleanup() {
        // Run cleanup every hour
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            logger.debug("Running block log retention cleanup...");
            // Redis keys expire automatically via setex, so no manual cleanup needed
            logger.debug("Block log retention cleanup complete (using Redis TTL)");
        }).repeat(TaskSchedule.duration(Duration.ofHours(1))).schedule();
    }

    /**
     * Check if a player is in inspect mode.
     */
    public boolean isInspectMode(UUID playerUuid) {
        return inspectModePlayers.contains(playerUuid);
    }

    /**
     * Get the number of pending async logging operations.
     */
    public long getPendingOperations() {
        return pendingOperations;
    }

    /**
     * Record of a block change.
     */
    private static class BlockChangeRecord {
        final String playerUuid;
        final String playerName;
        final long time;
        final String fromBlock;
        final String toBlock;
        final String action;
        final int x, y, z;

        BlockChangeRecord(String playerUuid, String playerName, long time,
                          String fromBlock, String toBlock, String action,
                          int x, int y, int z) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.time = time;
            this.fromBlock = fromBlock;
            this.toBlock = toBlock;
            this.action = action;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        String serialize() {
            return String.join(":",
                    playerUuid,
                    playerName,
                    String.valueOf(time),
                    fromBlock,
                    toBlock,
                    action,
                    String.valueOf(x),
                    String.valueOf(y),
                    String.valueOf(z));
        }

        static BlockChangeRecord deserialize(String str) {
            String[] parts = str.split(":");
            if (parts.length != 9) return null;

            try {
                return new BlockChangeRecord(
                        parts[0], // playerUuid
                        parts[1], // playerName
                        Long.parseLong(parts[2]), // time
                        parts[3], // fromBlock
                        parts[4], // toBlock
                        parts[5], // action
                        Integer.parseInt(parts[6]), // x
                        Integer.parseInt(parts[7]), // y
                        Integer.parseInt(parts[8])  // z
                );
            } catch (Exception e) {
                return null;
            }
        }
    }
}
