package com.shardedmc.shard;

import com.shardedmc.shared.ChunkPos;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.scoreboard.Sidebar;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Debug GUI system that displays sharding information to players in-game.
 * Shows real-time data about chunk boundaries, shard assignments, and performance metrics.
 */
public class ShardDebugGUI {
    private static final Logger logger = LoggerFactory.getLogger(ShardDebugGUI.class);
    
    private final String shardId;
    private final int port;
    private final int capacity;
    private final ShardCoordinatorClient coordinatorClient;
    
    // Player GUI state
    private final Map<UUID, Sidebar> playerSidebars = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> playerBossBars = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> debugMode = new ConcurrentHashMap<>();
    
    // Colors for different metrics
    private static final TextColor COLOR_PRIMARY = TextColor.fromHexString("#00FFAA");
    private static final TextColor COLOR_SECONDARY = TextColor.fromHexString("#FFAA00");
    private static final TextColor COLOR_WARNING = TextColor.fromHexString("#FF5555");
    private static final TextColor COLOR_INFO = TextColor.fromHexString("#55AAFF");
    
    public ShardDebugGUI(String shardId, int port, int capacity, ShardCoordinatorClient coordinatorClient) {
        this.shardId = shardId;
        this.port = port;
        this.capacity = capacity;
        this.coordinatorClient = coordinatorClient;
    }
    
    /**
     * Register all debug GUI events and commands
     */
    public void register(GlobalEventHandler eventHandler) {
        // Register movement listener for boundary detection
        eventHandler.addListener(PlayerMoveEvent.class, this::onPlayerMove);
        
        // Register spawn listener to initialize GUI
        eventHandler.addListener(PlayerSpawnEvent.class, event -> {
            initializePlayerGUI(event.getPlayer());
        });
        
        // Register tick listener for continuous updates
        eventHandler.addListener(PlayerTickEvent.class, event -> {
            if (debugMode.getOrDefault(event.getPlayer().getUuid(), false)) {
                updateDebugInfo(event.getPlayer());
            }
        });
        
        // Register commands
        registerCommands();
        
        // Start periodic updates for all players
        startPeriodicUpdates();
        
        logger.info("Shard Debug GUI registered for shard {}", shardId);
    }
    
    /**
     * Initialize GUI elements for a player
     */
    private void initializePlayerGUI(Player player) {
        UUID playerId = player.getUuid();
        
        // Create sidebar
        Sidebar sidebar = new Sidebar(Component.text("⚡ Shard Info").color(COLOR_PRIMARY));
        sidebar.addViewer(player);
        playerSidebars.put(playerId, sidebar);
        
        // Create boss bar for load
        BossBar bossBar = BossBar.bossBar(
                Component.text("Shard Load").color(COLOR_INFO),
                0.0f,
                BossBar.Color.GREEN,
                BossBar.Overlay.PROGRESS
        );
        player.showBossBar(bossBar);
        playerBossBars.put(playerId, bossBar);
        
        // Enable debug mode by default
        debugMode.put(playerId, true);
        
        // Send welcome message
        player.sendMessage(Component.text("╔══════════════════════════════════════╗").color(COLOR_PRIMARY));
        player.sendMessage(Component.text("║   ").color(COLOR_PRIMARY).append(Component.text("Welcome to ShardedMC!").color(COLOR_SECONDARY).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)).append(Component.text("   ║").color(COLOR_PRIMARY)));
        player.sendMessage(Component.text("╠══════════════════════════════════════╣").color(COLOR_PRIMARY));
        player.sendMessage(Component.text("║  ").color(COLOR_PRIMARY).append(Component.text("Shard: ").color(COLOR_INFO)).append(Component.text(shardId).color(COLOR_SECONDARY)).append(Component.text("                    ║").color(COLOR_PRIMARY)));
        player.sendMessage(Component.text("║  ").color(COLOR_PRIMARY).append(Component.text("Type ").color(COLOR_INFO)).append(Component.text("/shardinfo").color(COLOR_SECONDARY).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)).append(Component.text(" for details    ║").color(COLOR_PRIMARY)));
        player.sendMessage(Component.text("║  ").color(COLOR_PRIMARY).append(Component.text("Type ").color(COLOR_INFO)).append(Component.text("/sharddebug").color(COLOR_SECONDARY).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)).append(Component.text(" to toggle GUI  ║").color(COLOR_PRIMARY)));
        player.sendMessage(Component.text("╚══════════════════════════════════════╝").color(COLOR_PRIMARY));
        
        logger.info("Debug GUI initialized for player: {}", player.getUsername());
    }
    
    /**
     * Update debug information display
     */
    private void updateDebugInfo(Player player) {
        UUID playerId = player.getUuid();
        Pos pos = player.getPosition();
        ChunkPos chunkPos = ChunkPos.fromBlockPos((int) pos.x(), (int) pos.z());
        
        // Calculate distance to nearest chunk boundary
        int chunkLocalX = ((int) pos.x()) & 0xF;
        int chunkLocalZ = ((int) pos.z()) & 0xF;
        int distToBorderX = Math.min(chunkLocalX, 16 - chunkLocalX);
        int distToBorderZ = Math.min(chunkLocalZ, 16 - chunkLocalZ);
        int minDistToBorder = Math.min(distToBorderX, distToBorderZ);
        
        // Determine boundary warning level
        TextColor borderColor;
        String borderStatus;
        if (minDistToBorder <= 2) {
            borderColor = COLOR_WARNING;
            borderStatus = "⚠ BORDER";
        } else if (minDistToBorder <= 5) {
            borderColor = COLOR_SECONDARY;
            borderStatus = "⚡ NEAR";
        } else {
            borderColor = COLOR_PRIMARY;
            borderStatus = "✓ SAFE";
        }
        
        // Update action bar
        Component actionBar = Component.text("[Shard: ").color(COLOR_INFO)
                .append(Component.text(shardId).color(COLOR_SECONDARY))
                .append(Component.text("] ").color(COLOR_INFO))
                .append(Component.text("Chunk: ").color(COLOR_INFO))
                .append(Component.text(chunkPos.x() + ", " + chunkPos.z()).color(COLOR_SECONDARY))
                .append(Component.text(" | ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text("Border: ").color(COLOR_INFO))
                .append(Component.text(borderStatus).color(borderColor))
                .append(Component.text(" (" + minDistToBorder + " blocks)").color(NamedTextColor.GRAY));
        
        player.sendActionBar(actionBar);
        
        // Update sidebar
        Sidebar sidebar = playerSidebars.get(playerId);
        if (sidebar != null) {
            updateSidebar(sidebar, player, chunkPos, minDistToBorder, borderColor);
        }
        
        // Update boss bar with simulated load
        BossBar bossBar = playerBossBars.get(playerId);
        if (bossBar != null) {
            int onlinePlayers = MinecraftServer.getConnectionManager().getOnlinePlayers().size();
            float load = (float) onlinePlayers / capacity;
            bossBar.progress(load);
            
            // Change color based on load
            if (load < 0.5) {
                bossBar.color(BossBar.Color.GREEN);
            } else if (load < 0.8) {
                bossBar.color(BossBar.Color.YELLOW);
            } else {
                bossBar.color(BossBar.Color.RED);
            }
            
            bossBar.name(Component.text("Shard Load: ").color(COLOR_INFO)
                    .append(Component.text(String.format("%.0f%%", load * 100)).color(
                            load < 0.5 ? COLOR_PRIMARY : (load < 0.8 ? COLOR_SECONDARY : COLOR_WARNING)))
                    .append(Component.text(" (" + onlinePlayers + "/" + capacity + " players)").color(NamedTextColor.GRAY)));
        }
    }
    
    /**
     * Update sidebar with detailed information
     */
    private void updateSidebar(Sidebar sidebar, Player player, ChunkPos chunkPos, int borderDist, TextColor borderColor) {
        Pos pos = player.getPosition();
        int onlinePlayers = MinecraftServer.getConnectionManager().getOnlinePlayers().size();
        
        sidebar.createLine(new Sidebar.ScoreboardLine("shard", 
                Component.text("Shard: ").color(COLOR_INFO).append(Component.text(shardId).color(COLOR_SECONDARY)), 
                10));
        
        sidebar.createLine(new Sidebar.ScoreboardLine("port", 
                Component.text("Port: ").color(COLOR_INFO).append(Component.text(String.valueOf(port)).color(NamedTextColor.WHITE)), 
                9));
        
        sidebar.createLine(new Sidebar.ScoreboardLine("players", 
                Component.text("Players: ").color(COLOR_INFO).append(Component.text(onlinePlayers + "/" + capacity).color(COLOR_SECONDARY)), 
                8));
        
        sidebar.createLine(new Sidebar.ScoreboardLine("empty1", Component.empty(), 7));
        
        sidebar.createLine(new Sidebar.ScoreboardLine("position", 
                Component.text("Position:").color(COLOR_PRIMARY).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD), 
                6));
        
        sidebar.createLine(new Sidebar.ScoreboardLine("coords", 
                Component.text("  X: ").color(COLOR_INFO).append(Component.text(String.format("%.1f", pos.x())).color(NamedTextColor.WHITE))
                .append(Component.text(" Y: ").color(COLOR_INFO)).append(Component.text(String.format("%.1f", pos.y())).color(NamedTextColor.WHITE))
                .append(Component.text(" Z: ").color(COLOR_INFO)).append(Component.text(String.format("%.1f", pos.z())).color(NamedTextColor.WHITE)), 
                5));
        
        sidebar.createLine(new Sidebar.ScoreboardLine("chunk", 
                Component.text("Chunk: ").color(COLOR_INFO).append(Component.text(chunkPos.x() + ", " + chunkPos.z()).color(NamedTextColor.WHITE)), 
                4));
        
        sidebar.createLine(new Sidebar.ScoreboardLine("empty2", Component.empty(), 3));
        
        sidebar.createLine(new Sidebar.ScoreboardLine("boundary", 
                Component.text("Boundary:").color(COLOR_PRIMARY).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD), 
                2));
        
        sidebar.createLine(new Sidebar.ScoreboardLine("border_dist", 
                Component.text("  Distance: ").color(COLOR_INFO).append(Component.text(borderDist + " blocks").color(borderColor)), 
                1));
        
        sidebar.createLine(new Sidebar.ScoreboardLine("status", 
                Component.text("  Status: ").color(COLOR_INFO).append(Component.text(
                        borderDist <= 2 ? "⚠ TRANSFER ZONE" : (borderDist <= 5 ? "⚡ NEAR BORDER" : "✓ CENTERED")
                ).color(borderColor)), 
                0));
    }
    
    /**
     * Handle player movement for boundary warnings
     */
    private void onPlayerMove(PlayerMoveEvent event) {
        if (!debugMode.getOrDefault(event.getPlayer().getUuid(), false)) return;
        
        Pos newPos = event.getNewPosition();
        int chunkLocalX = ((int) newPos.x()) & 0xF;
        int chunkLocalZ = ((int) newPos.z()) & 0xF;
        int distToBorderX = Math.min(chunkLocalX, 16 - chunkLocalX);
        int distToBorderZ = Math.min(chunkLocalZ, 16 - chunkLocalZ);
        int minDistToBorder = Math.min(distToBorderX, distToBorderZ);
        
        // Show title warning when very close to boundary
        if (minDistToBorder <= 2) {
            Component title = Component.text("⚠ CHUNK BOUNDARY ⚠").color(COLOR_WARNING).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
            Component subtitle = Component.text("You are near a shard boundary!").color(NamedTextColor.YELLOW);
            
            event.getPlayer().showTitle(Title.title(title, subtitle, 
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(200))));
        }
    }
    
    /**
     * Register debug commands
     */
    private void registerCommands() {
        // /shardinfo command
        net.minestom.server.command.builder.Command shardInfoCmd = new net.minestom.server.command.builder.Command("shardinfo");
        shardInfoCmd.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            
            Pos pos = player.getPosition();
            ChunkPos chunkPos = ChunkPos.fromBlockPos((int) pos.x(), (int) pos.z());
            int onlinePlayers = MinecraftServer.getConnectionManager().getOnlinePlayers().size();
            
            int chunkLocalX = ((int) pos.x()) & 0xF;
            int chunkLocalZ = ((int) pos.z()) & 0xF;
            
            player.sendMessage(Component.text("\n╔════════════════════════════════════════════╗").color(COLOR_PRIMARY));
            player.sendMessage(Component.text("║         SHARD DEBUG INFORMATION          ║").color(COLOR_SECONDARY).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
            player.sendMessage(Component.text("╠════════════════════════════════════════════╣").color(COLOR_PRIMARY));
            player.sendMessage(Component.text("║  Shard ID:        ").color(COLOR_INFO).append(Component.text(shardId).color(COLOR_SECONDARY)).append(Component.text("                    ║").color(COLOR_PRIMARY)));
            player.sendMessage(Component.text("║  Port:            ").color(COLOR_INFO).append(Component.text(String.valueOf(port)).color(NamedTextColor.WHITE)).append(Component.text("                          ║").color(COLOR_PRIMARY)));
            player.sendMessage(Component.text("║  Capacity:        ").color(COLOR_INFO).append(Component.text(capacity).color(NamedTextColor.WHITE)).append(Component.text("                          ║").color(COLOR_PRIMARY)));
            player.sendMessage(Component.text("║  Online Players:  ").color(COLOR_INFO).append(Component.text(String.valueOf(onlinePlayers)).color(COLOR_SECONDARY)).append(Component.text("                        ║").color(COLOR_PRIMARY)));
            player.sendMessage(Component.text("║  Load:            ").color(COLOR_INFO).append(Component.text(String.format("%.1f%%", (onlinePlayers * 100.0 / capacity))).color(
                    onlinePlayers < capacity * 0.5 ? COLOR_PRIMARY : (onlinePlayers < capacity * 0.8 ? COLOR_SECONDARY : COLOR_WARNING))).append(Component.text("                       ║").color(COLOR_PRIMARY)));
            player.sendMessage(Component.text("╠════════════════════════════════════════════╣").color(COLOR_PRIMARY));
            player.sendMessage(Component.text("║  Your Position:").color(COLOR_PRIMARY).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD).append(Component.text("                           ║").color(COLOR_PRIMARY)));
            player.sendMessage(Component.text("║    World: ").color(COLOR_INFO).append(Component.text(String.format("%.2f, %.2f, %.2f", pos.x(), pos.y(), pos.z())).color(NamedTextColor.WHITE)).append(Component.text("      ║").color(COLOR_PRIMARY)));
            player.sendMessage(Component.text("║    Chunk: ").color(COLOR_INFO).append(Component.text(chunkPos.x() + ", " + chunkPos.z()).color(NamedTextColor.WHITE)).append(Component.text("                  ║").color(COLOR_PRIMARY)));
            player.sendMessage(Component.text("║    Local: ").color(COLOR_INFO).append(Component.text(chunkLocalX + ", " + chunkLocalZ).color(NamedTextColor.WHITE)).append(Component.text(" (within chunk)    ║").color(COLOR_PRIMARY)));
            player.sendMessage(Component.text("╠════════════════════════════════════════════╣").color(COLOR_PRIMARY));
            player.sendMessage(Component.text("║  Chunk Boundary:").color(COLOR_PRIMARY).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD).append(Component.text("                         ║").color(COLOR_PRIMARY)));
            player.sendMessage(Component.text("║    Distance X: ").color(COLOR_INFO).append(Component.text(Math.min(chunkLocalX, 16 - chunkLocalX) + " blocks").color(NamedTextColor.WHITE)).append(Component.text("            ║").color(COLOR_PRIMARY)));
            player.sendMessage(Component.text("║    Distance Z: ").color(COLOR_INFO).append(Component.text(Math.min(chunkLocalZ, 16 - chunkLocalZ) + " blocks").color(NamedTextColor.WHITE)).append(Component.text("            ║").color(COLOR_PRIMARY)));
            player.sendMessage(Component.text("╚════════════════════════════════════════════╝\n").color(COLOR_PRIMARY));
        });
        MinecraftServer.getCommandManager().register(shardInfoCmd);
        
        // /sharddebug command
        net.minestom.server.command.builder.Command shardDebugCmd = new net.minestom.server.command.builder.Command("sharddebug");
        shardDebugCmd.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            
            UUID playerId = player.getUuid();
            boolean newMode = !debugMode.getOrDefault(playerId, false);
            debugMode.put(playerId, newMode);
            
            if (newMode) {
                player.sendMessage(Component.text("✓ Debug GUI enabled!").color(COLOR_PRIMARY));
                initializePlayerGUI(player);
            } else {
                player.sendMessage(Component.text("✗ Debug GUI disabled!").color(COLOR_WARNING));
                cleanupPlayerGUI(player);
            }
        });
        MinecraftServer.getCommandManager().register(shardDebugCmd);
        
        // /shardchunks command
        net.minestom.server.command.builder.Command shardChunksCmd = new net.minestom.server.command.builder.Command("shardchunks");
        shardChunksCmd.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            
            Pos pos = player.getPosition();
            ChunkPos currentChunk = ChunkPos.fromBlockPos((int) pos.x(), (int) pos.z());
            
            player.sendMessage(Component.text("\n=== Chunk Grid (you are at ★) ===").color(COLOR_PRIMARY).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
            
            for (int cz = currentChunk.z() - 3; cz <= currentChunk.z() + 3; cz++) {
                net.kyori.adventure.text.TextComponent.Builder line = net.kyori.adventure.text.Component.text();
                for (int cx = currentChunk.x() - 3; cx <= currentChunk.x() + 3; cx++) {
                    if (cx == currentChunk.x() && cz == currentChunk.z()) {
                        line.append(Component.text(" ★ ").color(COLOR_SECONDARY).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
                    } else {
                        line.append(Component.text(" □ ").color(NamedTextColor.DARK_GRAY));
                    }
                }
                player.sendMessage(line.build());
            }
            
            player.sendMessage(Component.text("=================================\n").color(COLOR_PRIMARY));
        });
        MinecraftServer.getCommandManager().register(shardChunksCmd);
    }
    
    /**
     * Start periodic updates for all players
     */
    private void startPeriodicUpdates() {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                if (debugMode.getOrDefault(player.getUuid(), false)) {
                    updateDebugInfo(player);
                }
            }
        }).repeat(TaskSchedule.tick(10)).schedule(); // Update every 10 ticks (0.5 seconds)
    }
    
    /**
     * Cleanup GUI elements for a player
     */
    private void cleanupPlayerGUI(Player player) {
        UUID playerId = player.getUuid();
        
        Sidebar sidebar = playerSidebars.remove(playerId);
        if (sidebar != null) {
            sidebar.removeViewer(player);
        }
        
        BossBar bossBar = playerBossBars.remove(playerId);
        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
        
        player.sendActionBar(Component.empty());
    }
    
    /**
     * Remove player from tracking
     */
    public void removePlayer(UUID playerId) {
        debugMode.remove(playerId);
        playerSidebars.remove(playerId);
        playerBossBars.remove(playerId);
    }
}
