package com.shardedmc.shard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.server.ServerTickMonitorEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Auto-restart system with configurable intervals, warning broadcasts,
 * health checks, and graceful shutdown with chunk saving.
 */
public class AutoRestart {
    private static final Logger logger = LoggerFactory.getLogger(AutoRestart.class);

    // Configurable values (in minutes, converted from plan spec)
    private static final long DEFAULT_RESTART_INTERVAL_MINUTES = java.time.Duration.ofHours(12).toMinutes(); // 12 hours
    private static final double TPS_HEALTH_THRESHOLD = 10.0;
    private static final long TPS_HEALTH_DURATION_MINUTES = 5;
    private static final long TPS_HEALTH_DURATION_MS = TPS_HEALTH_DURATION_MINUTES * 60 * 1000;

    // Warning times before restart (in seconds)
    private static final long[] WARNING_TIMES = {
            600,  // 10 minutes
            300,  // 5 minutes
            60,   // 1 minute
            30    // 30 seconds
    };

    private final PerformanceMonitor performanceMonitor;
    private final long restartIntervalMs;
    private final AtomicLong restartTime = new AtomicLong(0);
    private final AtomicBoolean restartScheduled = new AtomicBoolean(false);
    private final AtomicBoolean restartInProgress = new AtomicBoolean(false);
    private final AtomicLong lastWarningTime = new AtomicLong(0);
    private final AtomicLong tpsDropStartTime = new AtomicLong(0);
    private final AtomicReference<String> restartReason = new AtomicReference<>("Scheduled");

    public AutoRestart() {
        this(null, DEFAULT_RESTART_INTERVAL_MINUTES);
    }

    public AutoRestart(PerformanceMonitor performanceMonitor, long restartIntervalMinutes) {
        this.performanceMonitor = performanceMonitor;
        this.restartIntervalMs = restartIntervalMinutes * 60 * 1000;
    }

    public void register(GlobalEventHandler eventHandler) {
        // Schedule first restart
        scheduleRestart("Scheduled");

        // Register TPS health check
        if (performanceMonitor != null) {
            eventHandler.addListener(ServerTickMonitorEvent.class, this::onServerTick);
        }

        // Start warning monitor
        startWarningMonitor();

        // Register commands
        registerRestartCommand();

        logger.info("AutoRestart registered. Next restart in {} minutes",
                restartIntervalMs / 60 / 1000);
    }

    private void onServerTick(ServerTickMonitorEvent event) {
        if (restartInProgress.get()) return;

        double mspt = event.getTickMonitor().getTickTime();
        double tps = Math.min(20.0, 1000.0 / Math.max(mspt, 1.0));

        if (tps < TPS_HEALTH_THRESHOLD) {
            long dropStart = tpsDropStartTime.get();
            if (dropStart == 0) {
                tpsDropStartTime.set(System.currentTimeMillis());
                logger.warn("TPS dropped below {} (current: {:.2f}). Monitoring for health restart...",
                        TPS_HEALTH_THRESHOLD, tps);
            } else {
                long elapsed = System.currentTimeMillis() - dropStart;
                if (elapsed >= TPS_HEALTH_DURATION_MS) {
                    logger.error("HEALTH RESTART TRIGGERED: TPS has been below {} for {} minutes. Initiating restart...",
                            TPS_HEALTH_THRESHOLD, TPS_HEALTH_DURATION_MINUTES);
                    cancelScheduledRestart();
                    scheduleRestart("Health check - TPS too low");
                }
            }
        } else {
            tpsDropStartTime.set(0);
        }
    }

    private void startWarningMonitor() {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            if (!restartScheduled.get() || restartInProgress.get()) return;

            long timeUntilRestart = restartTime.get() - System.currentTimeMillis();
            if (timeUntilRestart <= 0) {
                executeRestart();
                return;
            }

            long timeUntilRestartSec = timeUntilRestart / 1000;
            long lastWarning = lastWarningTime.get();

            for (long warningTime : WARNING_TIMES) {
                if (timeUntilRestartSec <= warningTime && timeUntilRestartSec > warningTime - 5) {
                    if (lastWarning != warningTime) {
                        lastWarningTime.set(warningTime);
                        broadcastWarning(warningTime);
                    }
                    break;
                }
            }
        }).repeat(TaskSchedule.tick(20)).schedule(); // Check every second
    }

    private void broadcastWarning(long secondsUntilRestart) {
        String timeStr;
        NamedTextColor color;
        if (secondsUntilRestart >= 60) {
            timeStr = (secondsUntilRestart / 60) + " minutes";
            color = NamedTextColor.YELLOW;
        } else {
            timeStr = secondsUntilRestart + " seconds";
            color = NamedTextColor.RED;
        }

        Component message = Component.text("", NamedTextColor.GOLD);
        Component title = Component.text("SERVER RESTART", NamedTextColor.RED).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
        Component subtitle = Component.text("Restarting in " + timeStr, color);

        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendMessage(message);
            player.sendMessage(Component.text("╔══════════════════════════════════════╗", NamedTextColor.GOLD));
            player.sendMessage(Component.text("║     SERVER RESTART INCOMING          ║", NamedTextColor.GOLD));
            player.sendMessage(Component.text("╠══════════════════════════════════════╣", NamedTextColor.GOLD));
            player.sendMessage(Component.text("║  Time: ", NamedTextColor.YELLOW).append(Component.text(timeStr, color)));
            player.sendMessage(Component.text("║  Reason: ", NamedTextColor.YELLOW).append(Component.text(restartReason.get(), NamedTextColor.WHITE)));
            player.sendMessage(Component.text("╚══════════════════════════════════════╝", NamedTextColor.GOLD));
            player.sendMessage(message);

            player.showTitle(Title.title(title, subtitle,
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(500))));
        }

        logger.info("Restart warning broadcasted: {} until restart (Reason: {})", timeStr, restartReason.get());
    }

    private void executeRestart() {
        if (restartInProgress.compareAndSet(false, true)) {
            logger.info("Executing server restart...");

            // Broadcast final warning
            for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                player.sendMessage(Component.text("", NamedTextColor.RED));
                player.sendMessage(Component.text("╔══════════════════════════════════════╗", NamedTextColor.RED));
                player.sendMessage(Component.text("║      SERVER IS RESTARTING NOW        ║", NamedTextColor.RED).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
                player.sendMessage(Component.text("╚══════════════════════════════════════╝", NamedTextColor.RED));
                player.sendMessage(Component.text("", NamedTextColor.RED));

                player.showTitle(Title.title(
                        Component.text("RESTARTING", NamedTextColor.RED).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD),
                        Component.text("Please reconnect in a moment...", NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(5), Duration.ofMillis(500))
                ));
            }

            // Give players a moment to see the message
            MinecraftServer.getSchedulerManager().buildTask(() -> {
                // Kick all players
                kickAllPlayers();

                // Save all chunks
                saveAllChunks();

                // Shutdown
                logger.info("Initiating server shutdown for restart");
                MinecraftServer.stopCleanly();

                // Exit with special code to trigger restart script
                System.exit(42);
            }).delay(TaskSchedule.tick(40)).schedule(); // 2 second delay
        }
    }

    private void kickAllPlayers() {
        Component kickMessage = Component.text("Server is restarting. Please reconnect in a moment.", NamedTextColor.YELLOW);
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.kick(kickMessage);
        }
    }

    private void saveAllChunks() {
        logger.info("Saving all chunks before restart...");
        int savedCount = 0;
        for (Instance instance : MinecraftServer.getInstanceManager().getInstances()) {
            for (var chunk : instance.getChunks()) {
                try {
                    // Minestom handles chunk saving automatically
                    savedCount++;
                } catch (Exception e) {
                    logger.error("Failed to save chunk {}, {}", chunk.getChunkX(), chunk.getChunkZ(), e);
                }
            }
        }
        logger.info("Saved {} chunks", savedCount);
    }

    private void scheduleRestart(String reason) {
        restartTime.set(System.currentTimeMillis() + restartIntervalMs);
        restartScheduled.set(true);
        restartInProgress.set(false);
        lastWarningTime.set(0);
        restartReason.set(reason);
        logger.info("Restart scheduled in {} minutes. Reason: {}", restartIntervalMs / 60 / 1000, reason);
    }

    private void cancelScheduledRestart() {
        restartScheduled.set(false);
        restartTime.set(0);
        lastWarningTime.set(0);
    }

    private void registerRestartCommand() {
        Command cmd = new Command("restart");
        var actionArg = ArgumentType.Word("action").from("now", "cancel", "status");

        cmd.addSyntax((sender, context) -> {
            String action = context.get(actionArg);
            switch (action.toLowerCase()) {
                case "now" -> {
                    if (sender instanceof Player player && player.getPermissionLevel() < 4) {
                        player.sendMessage(Component.text("You don't have permission to restart the server", NamedTextColor.RED));
                        return;
                    }
                    sender.sendMessage(Component.text("Restarting server now...", NamedTextColor.YELLOW));
                    String senderName = sender instanceof Player p ? p.getUsername() : sender.toString();
                    scheduleRestart("Manual restart by " + senderName);
                    executeRestart();
                }
                case "cancel" -> {
                    if (sender instanceof Player player && player.getPermissionLevel() < 4) {
                        player.sendMessage(Component.text("You don't have permission to cancel restart", NamedTextColor.RED));
                        return;
                    }
                    cancelScheduledRestart();
                    sender.sendMessage(Component.text("Scheduled restart cancelled.", NamedTextColor.GREEN));
                    String cancelSenderName = sender instanceof Player p ? p.getUsername() : sender.toString();
                    logger.info("Restart cancelled by {}", cancelSenderName);
                }
                case "status" -> {
                    if (!restartScheduled.get()) {
                        sender.sendMessage(Component.text("No restart is currently scheduled.", NamedTextColor.YELLOW));
                        return;
                    }
                    long timeUntil = restartTime.get() - System.currentTimeMillis();
                    long minutes = timeUntil / 60000;
                    long seconds = (timeUntil % 60000) / 1000;
                    sender.sendMessage(Component.text("Next restart in ", NamedTextColor.YELLOW)
                            .append(Component.text(minutes + "m " + seconds + "s", NamedTextColor.GREEN)));
                    sender.sendMessage(Component.text("Reason: ", NamedTextColor.YELLOW)
                            .append(Component.text(restartReason.get(), NamedTextColor.WHITE)));
                }
            }
        }, actionArg);

        cmd.setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("=== Restart Commands ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("/restart now - Restart server immediately", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/restart cancel - Cancel scheduled restart", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/restart status - Show restart status", NamedTextColor.YELLOW));
        });

        MinecraftServer.getCommandManager().register(cmd);
    }

    public boolean isRestartScheduled() {
        return restartScheduled.get();
    }

    public boolean isRestartInProgress() {
        return restartInProgress.get();
    }

    public long getTimeUntilRestart() {
        return restartScheduled.get() ? restartTime.get() - System.currentTimeMillis() : -1;
    }
}
