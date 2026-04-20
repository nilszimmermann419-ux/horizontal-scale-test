package com.shardedmc.shard;

import com.shardedmc.shared.RedisClient;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Chunk pregenerator that generates chunks in a spiral pattern from spawn.
 * Supports pause/resume, progress tracking, and Redis persistence.
 * Low priority to avoid impacting player experience.
 */
public class ChunkPregenerator {
    private static final Logger logger = LoggerFactory.getLogger(ChunkPregenerator.class);

    // Configurable values
    private static final int DEFAULT_CHUNKS_PER_TICK = 1;
    private static final int MAX_MSPT_FOR_GENERATION = 40; // Pause if MSPT exceeds this
    private static final int YIELD_TICKS = 1; // Yield between chunks
    private static final String REDIS_KEY_PREFIX = "pregen:";

    private final RedisClient redisClient;
    private final String shardId;
    private final Instance instance;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicInteger currentRadius = new AtomicInteger(0);
    private final AtomicInteger totalChunks = new AtomicInteger(0);
    private final AtomicInteger generatedChunks = new AtomicInteger(0);
    private final AtomicLong startTime = new AtomicLong(0);
    private final AtomicReference<Pos> centerPos = new AtomicReference<>();

    public ChunkPregenerator(RedisClient redisClient, String shardId, Instance instance) {
        this.redisClient = redisClient;
        this.shardId = shardId;
        this.instance = instance;
    }

    public void register() {
        registerPregenCommand();
        loadStatusFromRedis();
        logger.info("ChunkPregenerator registered for shard {}", shardId);
    }

    private void registerPregenCommand() {
        Command cmd = new Command("pregen");
        var radiusArg = ArgumentType.Integer("radius").min(1).max(100);
        var actionArg = ArgumentType.Word("action").from("start", "pause", "resume", "status", "cancel");

        // /pregen start <radius>
        cmd.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can use this command", NamedTextColor.RED));
                return;
            }
            int radius = context.get(radiusArg);
            startPregen(player, radius);
        }, actionArg, radiusArg);

        // /pregen pause|resume|status|cancel
        cmd.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can use this command", NamedTextColor.RED));
                return;
            }
            String action = context.get(actionArg);
            switch (action.toLowerCase()) {
                case "pause" -> pausePregen(player);
                case "resume" -> resumePregen(player);
                case "status" -> showStatus(player);
                case "cancel" -> cancelPregen(player);
                default -> player.sendMessage(Component.text("Usage: /pregen <start|pause|resume|status|cancel> [radius]", NamedTextColor.RED));
            }
        }, actionArg);

        cmd.setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("=== Chunk Pregenerator ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("/pregen start <radius> - Start pregeneration", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/pregen pause - Pause pregeneration", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/pregen resume - Resume pregeneration", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/pregen status - Show progress", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/pregen cancel - Cancel pregeneration", NamedTextColor.YELLOW));
        });

        MinecraftServer.getCommandManager().register(cmd);
    }

    private void startPregen(Player player, int radius) {
        if (running.get()) {
            player.sendMessage(Component.text("Pregeneration is already running! Use /pregen status", NamedTextColor.RED));
            return;
        }

        running.set(true);
        paused.set(false);
        currentRadius.set(0);
        generatedChunks.set(0);
        startTime.set(System.currentTimeMillis());
        centerPos.set(player.getPosition());

        // Calculate total chunks in spiral
        int total = calculateTotalChunks(radius);
        totalChunks.set(total);

        player.sendMessage(Component.text("Starting chunk pregeneration...", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Radius: " + radius + " chunks", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Total chunks: " + total, NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Center: " + (int) player.getPosition().x() + ", " + (int) player.getPosition().z(), NamedTextColor.YELLOW));

        logger.info("Player {} started chunk pregeneration with radius {} ({} chunks)",
                player.getUsername(), radius, total);

        // Start generation task
        generateSpiral(player, radius);
    }

    private void generateSpiral(Player initiator, int maxRadius) {
        Pos center = centerPos.get();
        int centerChunkX = center.chunkX();
        int centerChunkZ = center.chunkZ();

        MinecraftServer.getSchedulerManager().buildTask(() -> {
            if (!running.get()) return;
            if (paused.get()) return;

            // Check MSPT - pause if server is lagging
            // Use ShardMonitor for tick time if available, otherwise skip check
            double mspt = 0;
            try {
                mspt = com.shardedmc.shard.debug.ShardMonitor.getInstance().getCurrentTickTime();
            } catch (Exception e) {
                // ShardMonitor not available, skip MSPT check
            }
            if (mspt > MAX_MSPT_FOR_GENERATION) {
                return; // Skip this tick, try again next tick
            }

            int radius = currentRadius.get();
            if (radius > maxRadius) {
                // Done!
                running.set(false);
                long elapsed = System.currentTimeMillis() - startTime.get();
                String timeStr = formatDuration(elapsed);

                Component doneMsg = Component.text("Chunk pregeneration complete!", NamedTextColor.GREEN);
                Component statsMsg = Component.text("Generated " + generatedChunks.get() + " chunks in " + timeStr, NamedTextColor.YELLOW);

                broadcastToOps(doneMsg);
                broadcastToOps(statsMsg);
                logger.info("Chunk pregeneration complete. Generated {} chunks in {}", generatedChunks.get(), timeStr);

                clearStatusInRedis();
                return;
            }

            // Generate chunks for this radius in spiral order
            int chunksThisTick = 0;
            int x = 0, z = 0;
            int dx = 0, dz = -1;
            int targetRadius = radius;

            // Generate all chunks at current radius
            for (int i = 0; i < (targetRadius * 2 + 1) * (targetRadius * 2 + 1); i++) {
                if (-targetRadius <= x && x <= targetRadius && -targetRadius <= z && z <= targetRadius) {
                    if (Math.max(Math.abs(x), Math.abs(z)) == targetRadius) {
                        // This is on the perimeter
                        int chunkX = centerChunkX + x;
                        int chunkZ = centerChunkZ + z;

                        final int cx = chunkX;
                        final int cz = chunkZ;

                        instance.loadChunk(cx, cz).thenRun(() -> {
                            generatedChunks.incrementAndGet();
                            saveStatusToRedis();
                        }).exceptionally(ex -> {
                            logger.error("Failed to generate chunk {}, {}", cx, cz, ex);
                            return null;
                        });

                        chunksThisTick++;
                        if (chunksThisTick >= DEFAULT_CHUNKS_PER_TICK) {
                            break;
                        }
                    }
                }

                if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                    int temp = dx;
                    dx = -dz;
                    dz = temp;
                }
                x += dx;
                z += dz;
            }

            if (chunksThisTick < DEFAULT_CHUNKS_PER_TICK) {
                currentRadius.incrementAndGet();
            }

            // Progress update every 5%
            int total = totalChunks.get();
            int generated = generatedChunks.get();
            if (total > 0 && generated % Math.max(1, total / 20) == 0) {
                double percent = (generated * 100.0) / total;
                long elapsed = System.currentTimeMillis() - startTime.get();
                long etaMs = (long) ((elapsed / percent) * (100 - percent));
                String etaStr = formatDuration(etaMs);

                if (initiator.isOnline()) {
                    initiator.sendActionBar(Component.text(
                            String.format("Pregen: %d/%d chunks (%.1f%%) - ETA: %s",
                                    generated, total, percent, etaStr), NamedTextColor.GREEN));
                }
            }

        }).repeat(TaskSchedule.tick(YIELD_TICKS)).schedule();
    }

    private int calculateTotalChunks(int radius) {
        int total = 0;
        for (int r = 0; r <= radius; r++) {
            if (r == 0) {
                total += 1;
            } else {
                total += r * 8;
            }
        }
        return total;
    }

    private void pausePregen(Player player) {
        if (!running.get()) {
            player.sendMessage(Component.text("No pregeneration is running!", NamedTextColor.RED));
            return;
        }
        paused.set(true);
        player.sendMessage(Component.text("Chunk pregeneration paused.", NamedTextColor.YELLOW));
        logger.info("Player {} paused chunk pregeneration", player.getUsername());
        saveStatusToRedis();
    }

    private void resumePregen(Player player) {
        if (!running.get()) {
            player.sendMessage(Component.text("No pregeneration is running!", NamedTextColor.RED));
            return;
        }
        paused.set(false);
        player.sendMessage(Component.text("Chunk pregeneration resumed.", NamedTextColor.GREEN));
        logger.info("Player {} resumed chunk pregeneration", player.getUsername());
        saveStatusToRedis();
    }

    private void showStatus(Player player) {
        if (!running.get()) {
            player.sendMessage(Component.text("No pregeneration is currently running.", NamedTextColor.YELLOW));
            return;
        }

        int total = totalChunks.get();
        int generated = generatedChunks.get();
        int radius = currentRadius.get();
        boolean isPaused = paused.get();
        long elapsed = System.currentTimeMillis() - startTime.get();

        double percent = total > 0 ? (generated * 100.0) / total : 0;
        long etaMs = percent > 0 ? (long) ((elapsed / percent) * (100 - percent)) : 0;

        player.sendMessage(Component.text("", NamedTextColor.GOLD));
        player.sendMessage(Component.text("╔════════════════════════════════════════╗", NamedTextColor.GOLD));
        player.sendMessage(Component.text("║      CHUNK PREGENERATION STATUS        ║", NamedTextColor.GOLD));
        player.sendMessage(Component.text("╠════════════════════════════════════════╣", NamedTextColor.GOLD));
        player.sendMessage(Component.text("║  Status:    ", NamedTextColor.YELLOW)
                .append(Component.text(isPaused ? "PAUSED" : "RUNNING",
                        isPaused ? NamedTextColor.RED : NamedTextColor.GREEN)));
        player.sendMessage(Component.text("║  Radius:    ", NamedTextColor.YELLOW)
                .append(Component.text(radius, NamedTextColor.WHITE)));
        player.sendMessage(Component.text("║  Progress:  ", NamedTextColor.YELLOW)
                .append(Component.text(generated + " / " + total + " chunks", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("║  Percent:   ", NamedTextColor.YELLOW)
                .append(Component.text(String.format("%.1f%%", percent), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("║  Elapsed:   ", NamedTextColor.YELLOW)
                .append(Component.text(formatDuration(elapsed), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("║  ETA:       ", NamedTextColor.YELLOW)
                .append(Component.text(formatDuration(etaMs), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("╚════════════════════════════════════════╝", NamedTextColor.GOLD));
        player.sendMessage(Component.text("", NamedTextColor.GOLD));
    }

    private void cancelPregen(Player player) {
        if (!running.get()) {
            player.sendMessage(Component.text("No pregeneration is running!", NamedTextColor.RED));
            return;
        }

        int generated = generatedChunks.get();
        running.set(false);
        paused.set(false);

        player.sendMessage(Component.text("Chunk pregeneration cancelled. Generated " + generated + " chunks.", NamedTextColor.YELLOW));
        logger.info("Player {} cancelled chunk pregeneration ({} chunks generated)", player.getUsername(), generated);
        clearStatusInRedis();
    }

    private void saveStatusToRedis() {
        if (redisClient == null) return;

        try {
            String key = REDIS_KEY_PREFIX + shardId;
            Map<String, String> status = Map.of(
                    "running", String.valueOf(running.get()),
                    "paused", String.valueOf(paused.get()),
                    "radius", String.valueOf(currentRadius.get()),
                    "generated", String.valueOf(generatedChunks.get()),
                    "total", String.valueOf(totalChunks.get()),
                    "startTime", String.valueOf(startTime.get())
            );
            redisClient.hsetAsync(key, status);
        } catch (Exception e) {
            logger.error("Failed to save pregen status to Redis", e);
        }
    }

    private void loadStatusFromRedis() {
        if (redisClient == null) return;

        try {
            String key = REDIS_KEY_PREFIX + shardId;
            Map<String, String> status = redisClient.hgetall(key);
            if (status != null && !status.isEmpty()) {
                logger.info("Loaded pregen status from Redis: {}", status);
                // We don't auto-resume, just log the status
            }
        } catch (Exception e) {
            logger.error("Failed to load pregen status from Redis", e);
        }
    }

    private void clearStatusInRedis() {
        if (redisClient == null) return;

        try {
            String key = REDIS_KEY_PREFIX + shardId;
            redisClient.delAsync(key);
        } catch (Exception e) {
            logger.error("Failed to clear pregen status from Redis", e);
        }
    }

    private String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return (ms / 1000) + "s";
        if (ms < 3600000) return (ms / 60000) + "m " + ((ms % 60000) / 1000) + "s";
        return (ms / 3600000) + "h " + ((ms % 3600000) / 60000) + "m";
    }

    private void broadcastToOps(Component message) {
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isPaused() {
        return paused.get();
    }
}
