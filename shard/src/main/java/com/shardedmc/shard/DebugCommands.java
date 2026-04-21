package com.shardedmc.shard;

import com.shardedmc.shard.debug.ShardMonitor;
import com.shardedmc.shard.debug.ShardMonitor.MemoryStats;
import com.shardedmc.shard.debug.ShardMonitor.TickStats;
import com.shardedmc.shard.vanilla.NPCManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.timer.TaskSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Debug commands for testing, development, and monitoring shard performance.
 * Includes comprehensive monitoring, chunk management, entity management,
 * performance metrics, and a debug boss bar panel.
 */
public class DebugCommands {
    private static final Logger logger = LoggerFactory.getLogger(DebugCommands.class);
    private static final ShardMonitor monitor = ShardMonitor.getInstance();

    // Debug panel state
    private static final Map<UUID, BossBar> debugPanels = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> panelEnabled = new ConcurrentHashMap<>();
    private static boolean panelTaskStarted = false;

    public static void registerAll() {
        registerDebugCommand();
        registerHealCommand();
        registerFeedCommand();
        registerGiveCommand();
        registerTpCommand();
        registerSpawnCommand();
        registerClearCommand();
        registerTimeCommand();
        registerBlockCommand();
        registerBotCommand();

        // Shard monitoring commands
        registerShardInfoCommand();
        registerTPSCommand();
        registerMemoryCommand();
        registerGcCommand();

        // Chunk commands
        registerChunkInfoCommand();
        registerChunkLoadCommand();
        registerChunkUnloadCommand();
        registerChunksCommand();

        // Entity commands
        registerEntityInfoCommand();
        registerEntityTypesCommand();
        registerClearEntitiesCommand();

        // Performance commands
        registerLagCommand();
        registerTimingsCommand();

        // Debug panel
        registerDebugPanelCommand();
        startDebugPanelTask();

        // NPC commands
        registerNPCCommand();

        logger.info("Debug commands registered (19 commands + debug panel)");
    }

    // ==================== Original Debug Commands ====================

    private static void registerDebugCommand() {
        Command cmd = new Command("debug");
        cmd.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can use this command", NamedTextColor.RED));
                return;
            }

            Pos pos = player.getPosition();
            player.sendMessage(Component.text("=== Debug Info ===", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Position: " + pos, NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Chunk: " + pos.chunkX() + ", " + pos.chunkZ(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Health: " + player.getHealth(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Food: " + player.getFood(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Saturation: " + player.getFoodSaturation(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Players online: " + MinecraftServer.getConnectionManager().getOnlinePlayers().size(), NamedTextColor.YELLOW));

            Block block = player.getInstance().getBlock(pos.blockX(), pos.blockY() - 1, pos.blockZ());
            player.sendMessage(Component.text("Ground block: " + block.name(), NamedTextColor.YELLOW));
        });

        MinecraftServer.getCommandManager().register(cmd);
    }

    private static void registerHealCommand() {
        Command cmd = new Command("heal");
        cmd.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            player.setHealth(20);
            player.setFood(20);
            player.setFoodSaturation(5.0f);
            player.sendMessage(Component.text("Healed!", NamedTextColor.GREEN));
        });
        MinecraftServer.getCommandManager().register(cmd);
    }

    private static void registerFeedCommand() {
        Command cmd = new Command("feed");
        cmd.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            player.setFood(20);
            player.setFoodSaturation(20.0f);
            player.sendMessage(Component.text("Fed!", NamedTextColor.GREEN));
        });
        MinecraftServer.getCommandManager().register(cmd);
    }

    private static void registerGiveCommand() {
        Command cmd = new Command("give");
        var materialArg = ArgumentType.Word("material").from(
                Material.values().stream().map(Material::name).toArray(String[]::new)
        );
        var amountArg = ArgumentType.Integer("amount").min(1).max(64);

        cmd.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            String matName = context.get(materialArg);
            int amount = context.get(amountArg);

            try {
                Material mat = Material.fromKey(matName.toLowerCase());
                if (mat != null) {
                    player.getInventory().addItemStack(ItemStack.of(mat, amount));
                    player.sendMessage(Component.text("Gave " + amount + "x " + matName, NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Invalid material: " + matName, NamedTextColor.RED));
                }
            } catch (Exception e) {
                player.sendMessage(Component.text("Invalid material: " + matName, NamedTextColor.RED));
            }
        }, materialArg, amountArg);

        cmd.setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("Usage: /give <material> <amount>", NamedTextColor.RED));
        });

        MinecraftServer.getCommandManager().register(cmd);
    }

    private static void registerTpCommand() {
        Command cmd = new Command("tp");
        var xArg = ArgumentType.Double("x");
        var yArg = ArgumentType.Double("y");
        var zArg = ArgumentType.Double("z");

        cmd.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            double x = context.get(xArg);
            double y = context.get(yArg);
            double z = context.get(zArg);
            player.teleport(new Pos(x, y, z));
            player.sendMessage(Component.text("Teleported to " + x + ", " + y + ", " + z, NamedTextColor.GREEN));
        }, xArg, yArg, zArg);

        cmd.setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("Usage: /tp <x> <y> <z>", NamedTextColor.RED));
        });

        MinecraftServer.getCommandManager().register(cmd);
    }

    private static void registerSpawnCommand() {
        Command cmd = new Command("spawn");
        cmd.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            player.teleport(player.getRespawnPoint());
            player.sendMessage(Component.text("Teleported to spawn!", NamedTextColor.GREEN));
        });
        MinecraftServer.getCommandManager().register(cmd);
    }

    private static void registerClearCommand() {
        Command cmd = new Command("clear");
        cmd.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            player.getInventory().clear();
            player.sendMessage(Component.text("Inventory cleared!", NamedTextColor.GREEN));
        });
        MinecraftServer.getCommandManager().register(cmd);
    }

    private static void registerTimeCommand() {
        Command cmd = new Command("time");
        var timeArg = ArgumentType.Long("time");

        cmd.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            long time = context.get(timeArg);
            player.getInstance().setWorldAge(time);
            player.sendMessage(Component.text("Time set to " + time, NamedTextColor.GREEN));
        }, timeArg);

        cmd.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            long time = player.getInstance().getWorldAge();
            sender.sendMessage(Component.text("Current time: " + time, NamedTextColor.YELLOW));
        });

        MinecraftServer.getCommandManager().register(cmd);
    }

    private static void registerBlockCommand() {
        Command cmd = new Command("block");
        var blockArg = ArgumentType.Word("block");

        cmd.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            String blockName = context.get(blockArg);

            Pos pos = player.getPosition();
            Block block = Block.fromKey(blockName.toLowerCase());
            if (block != null) {
                player.getInstance().setBlock(pos.blockX(), pos.blockY() - 1, pos.blockZ(), block);
                player.sendMessage(Component.text("Set block to " + blockName, NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Invalid block: " + blockName, NamedTextColor.RED));
            }
        }, blockArg);

        MinecraftServer.getCommandManager().register(cmd);
    }

    private static void registerBotCommand() {
        Command cmd = new Command("bot");
        var actionArg = ArgumentType.Word("action").from("spawn", "killall");

        cmd.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            String action = context.get(actionArg);

            if ("spawn".equals(action)) {
                spawnBot(player);
            } else if ("killall".equals(action)) {
                killAllBots(player);
            }
        }, actionArg);

        cmd.setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("Usage: /bot <spawn|killall>", NamedTextColor.RED));
        });

        MinecraftServer.getCommandManager().register(cmd);
    }

    private static void spawnBot(Player player) {
        Pos pos = player.getPosition().add(2, 0, 0);
        net.minestom.server.entity.Entity bot = new net.minestom.server.entity.Entity(net.minestom.server.entity.EntityType.ZOMBIE);
        bot.setInstance(player.getInstance(), pos);
        bot.setCustomName(Component.text("Test Bot"));
        bot.setCustomNameVisible(true);

        if (bot instanceof net.minestom.server.entity.LivingEntity living) {
            living.setHealth(20);
        }

        player.sendMessage(Component.text("Spawned a test bot!", NamedTextColor.GREEN));
        logger.info("Player {} spawned a bot at {}", player.getUsername(), pos);
    }

    private static void killAllBots(Player player) {
        int count = 0;
        for (var entity : player.getInstance().getEntities()) {
            if (entity.getEntityType() == net.minestom.server.entity.EntityType.ZOMBIE) {
                entity.remove();
                count++;
            }
        }
        player.sendMessage(Component.text("Killed " + count + " bots", NamedTextColor.GREEN));
    }

    // ==================== Shard Monitoring Commands ====================

    private static void registerShardInfoCommand() {
        Command cmd = new Command("shardinfo");
        cmd.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) return;

            int onlinePlayers = MinecraftServer.getConnectionManager().getOnlinePlayers().size();
            int entities = player.getInstance().getEntities().size();
            int loadedChunks = player.getInstance().getChunks().size();

            MemoryStats mem = monitor.getMemoryStats();
            long usedMemory = mem.heapUsed() / 1024 / 1024;
            long maxMemory = mem.heapMax() / 1024 / 1024;
            double tps = monitor.getCurrentTps();

            player.sendMessage(Component.text("", NamedTextColor.GOLD));
            player.sendMessage(Component.text("╔════════════════════════════════════════╗", NamedTextColor.GOLD));
            player.sendMessage(Component.text("║           SHARD INFORMATION            ║", NamedTextColor.GOLD));
            player.sendMessage(Component.text("╠════════════════════════════════════════╣", NamedTextColor.GOLD));
            player.sendMessage(Component.text("║  Players:      ", NamedTextColor.YELLOW)
                    .append(Component.text(onlinePlayers, NamedTextColor.WHITE)));
            player.sendMessage(Component.text("║  Entities:     ", NamedTextColor.YELLOW)
                    .append(Component.text(entities, NamedTextColor.WHITE)));
            player.sendMessage(Component.text("║  Loaded Chunks:", NamedTextColor.YELLOW)
                    .append(Component.text(loadedChunks, NamedTextColor.WHITE)));
            player.sendMessage(Component.text("║  Memory:       ", NamedTextColor.YELLOW)
                    .append(Component.text(usedMemory + "MB / " + maxMemory + "MB", NamedTextColor.WHITE)));
            player.sendMessage(Component.text("║  TPS:          ", NamedTextColor.YELLOW)
                    .append(Component.text(String.format("%.2f", tps),
                            tps >= 18 ? NamedTextColor.GREEN : (tps >= 15 ? NamedTextColor.YELLOW : NamedTextColor.RED))));
            player.sendMessage(Component.text("║  Tick Time:    ", NamedTextColor.YELLOW)
                    .append(Component.text(String.format("%.2f ms", monitor.getCurrentTickTime()), NamedTextColor.WHITE)));
            player.sendMessage(Component.text("║  View Distance:", NamedTextColor.YELLOW)
                    .append(Component.text("10", NamedTextColor.WHITE)));
            player.sendMessage(Component.text("╚════════════════════════════════════════╝", NamedTextColor.GOLD));
            player.sendMessage(Component.text("", NamedTextColor.GOLD));
        });
        MinecraftServer.getCommandManager().register(cmd);
    }

    private static void registerTPSCommand() {
        Command cmd = new Command("tps");
        cmd.setDefaultExecutor((sender, context) -> {
            double tps = monitor.getCurrentTps();
            double tickTime = monitor.getCurrentTickTime();
            long totalTicks = monitor.getTotalTicks();

            NamedTextColor color = tps >= 18 ? NamedTextColor.GREEN : (tps >= 15 ? NamedTextColor.YELLOW : NamedTextColor.RED);

            sender.sendMessage(Component.text("=== Server TPS ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Current TPS: ", NamedTextColor.YELLOW)
                    .append(Component.text(String.format("%.2f", tps), color)));
            sender.sendMessage(Component.text("Tick Time: ", NamedTextColor.YELLOW)
                    .append(Component.text(String.format("%.2f ms", tickTime),
                            tickTime <= 55 ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));
            sender.sendMessage(Component.text("Total Ticks: ", NamedTextColor.YELLOW)
                    .append(Component.text(String.valueOf(totalTicks), NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("Status: ", NamedTextColor.YELLOW)
                    .append(tps >= 18
                            ? Component.text("✓ Healthy", NamedTextColor.GREEN)
                            : Component.text("⚠ Degraded", NamedTextColor.RED)));
        });
        MinecraftServer.getCommandManager().register(cmd);
    }

    private static void registerMemoryCommand() {
        Command cmd = new Command("memory");
        cmd.setDefaultExecutor((sender, context) -> {
            MemoryStats mem = monitor.getMemoryStats();

            long heapUsed = mem.heapUsed() / 1024 / 1024;
            long heapCommitted = mem.heapCommitted() / 1024 / 1024;
            long heapMax = mem.heapMax() / 1024 / 1024;
            long nonHeapUsed = mem.nonHeapUsed() / 1024 / 1024;
            long nonHeapCommitted = mem.nonHeapCommitted() / 1024 / 1024;

            sender.sendMessage(Component.text("", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("╔════════════════════════════════════════╗", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("║           MEMORY STATISTICS            ║", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("╠════════════════════════════════════════╣", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("║  Heap Memory:", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("║    Used:      ", NamedTextColor.YELLOW)
                    .append(Component.text(heapUsed + " MB", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("║    Committed: ", NamedTextColor.YELLOW)
                    .append(Component.text(heapCommitted + " MB", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("║    Max:       ", NamedTextColor.YELLOW)
                    .append(Component.text(heapMax + " MB", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("║    Usage:     ", NamedTextColor.YELLOW)
                    .append(Component.text(String.format("%.1f%%", (double) heapUsed / heapMax * 100),
                            heapUsed < heapMax * 0.8 ? NamedTextColor.GREEN : NamedTextColor.RED)));
            sender.sendMessage(Component.text("╠════════════════════════════════════════╣", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("║  Non-Heap Memory:", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("║    Used:      ", NamedTextColor.YELLOW)
                    .append(Component.text(nonHeapUsed + " MB", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("║    Committed: ", NamedTextColor.YELLOW)
                    .append(Component.text(nonHeapCommitted + " MB", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("╠════════════════════════════════════════╣", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("║  Garbage Collection:", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("║    Total GCs:  ", NamedTextColor.YELLOW)
                    .append(Component.text(String.valueOf(mem.totalGcCount()), NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("║    GC Time:    ", NamedTextColor.YELLOW)
                    .append(Component.text(mem.totalGcTime() + " ms", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("║    Recent GCs: ", NamedTextColor.YELLOW)
                    .append(Component.text(String.valueOf(mem.gcCountDelta()), NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("╚════════════════════════════════════════╝", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("", NamedTextColor.GOLD));
        });
        MinecraftServer.getCommandManager().register(cmd);
    }

    private static void registerGcCommand() {
        Command cmd = new Command("gc");
        cmd.setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("Triggering garbage collection...", NamedTextColor.YELLOW));

            MemoryStats before = monitor.getMemoryStats();
            long beforeUsed = before.heapUsed();

            MemoryStats after = monitor.triggerGc();
            long afterUsed = after.heapUsed();

            long freed = (beforeUsed - afterUsed) / 1024 / 1024;

            sender.sendMessage(Component.text("", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("=== GC Complete ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Memory Freed: ", NamedTextColor.YELLOW)
                    .append(Component.text(freed + " MB", NamedTextColor.GREEN)));
            sender.sendMessage(Component.text("Heap After GC: ", NamedTextColor.YELLOW)
                    .append(Component.text(afterUsed / 1024 / 1024 + " MB", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("Total GC Count: ", NamedTextColor.YELLOW)
                    .append(Component.text(String.valueOf(after.totalGcCount()), NamedTextColor.WHITE)));
        });
        MinecraftServer.getCommandManager().register(cmd);
    }

    // ==================== Chunk Commands ====================

    private static void registerChunkInfoCommand() {
        Command cmd = new Command("chunkinfo");
        cmd.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) return;

            Chunk chunk = player.getChunk();
            if (chunk == null) {
                player.sendMessage(Component.text("You are not in a loaded chunk!", NamedTextColor.RED));
                return;
            }

            // TODO: Cache chunk-entity mappings to avoid iterating all entities
            int entityCount = 0;
            int chunkX = chunk.getChunkX();
            int chunkZ = chunk.getChunkZ();
            for (Entity entity : player.getInstance().getEntities()) {
                Pos entityPos = entity.getPosition();
                if (entityPos.chunkX() == chunkX && entityPos.chunkZ() == chunkZ) {
                    entityCount++;
                }
            }

            player.sendMessage(Component.text("", NamedTextColor.GOLD));
            player.sendMessage(Component.text("╔════════════════════════════════════════╗", NamedTextColor.GOLD));
            player.sendMessage(Component.text("║           CHUNK INFORMATION            ║", NamedTextColor.GOLD));
            player.sendMessage(Component.text("╠════════════════════════════════════════╣", NamedTextColor.GOLD));
            player.sendMessage(Component.text("║  Position:    ", NamedTextColor.YELLOW)
                    .append(Component.text(chunkX + ", " + chunkZ, NamedTextColor.WHITE)));
            player.sendMessage(Component.text("║  Biome:       ", NamedTextColor.YELLOW)
                    .append(Component.text(chunk.getBiome(0, 0, 0).toString(), NamedTextColor.WHITE)));
            player.sendMessage(Component.text("║  Entities:    ", NamedTextColor.YELLOW)
                    .append(Component.text(entityCount, NamedTextColor.WHITE)));
            player.sendMessage(Component.text("║  Sections:    ", NamedTextColor.YELLOW)
                    .append(Component.text(chunk.getSections().size(), NamedTextColor.WHITE)));
            player.sendMessage(Component.text("╚════════════════════════════════════════╝", NamedTextColor.GOLD));
            player.sendMessage(Component.text("", NamedTextColor.GOLD));
        });
        MinecraftServer.getCommandManager().register(cmd);
    }

    private static void registerChunkLoadCommand() {
        Command cmd = new Command("chunkload");
        var xArg = ArgumentType.Integer("x");
        var zArg = ArgumentType.Integer("z");

        cmd.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            int x = context.get(xArg);
            int z = context.get(zArg);

            player.sendMessage(Component.text("Loading chunk at " + x + ", " + z + "...", NamedTextColor.YELLOW));
            player.getInstance().loadChunk(x, z).thenRun(() -> {
                player.sendMessage(Component.text("✓ Chunk " + x + ", " + z + " loaded successfully!", NamedTextColor.GREEN));
            }).exceptionally(ex -> {
                player.sendMessage(Component.text("✗ Failed to load chunk: " + ex.getMessage(), NamedTextColor.RED));
                return null;
            });
        }, xArg, zArg);

        cmd.setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("Usage: /chunkload <x> <z>", NamedTextColor.RED));
        });

        MinecraftServer.getCommandManager().register(cmd);
    }

    private static void registerChunkUnloadCommand() {
        Command cmd = new Command("chunkunload");
        var xArg = ArgumentType.Integer("x");
        var zArg = ArgumentType.Integer("z");

        cmd.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            int x = context.get(xArg);
            int z = context.get(zArg);

            Chunk chunk = player.getInstance().getChunk(x, z);
            if (chunk == null) {
                player.sendMessage(Component.text("Chunk " + x + ", " + z + " is not loaded!", NamedTextColor.RED));
                return;
            }

            player.sendMessage(Component.text("Unloading chunk at " + x + ", " + z + "...", NamedTextColor.YELLOW));
            player.getInstance().unloadChunk(chunk);
            player.sendMessage(Component.text("✓ Chunk " + x + ", " + z + " unloaded!", NamedTextColor.GREEN));
        }, xArg, zArg);

        cmd.setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("Usage: /chunkunload <x> <z>", NamedTextColor.RED));
        });

        MinecraftServer.getCommandManager().register(cmd);
    }

    private static void registerChunksCommand() {
        Command cmd = new Command("chunks");
        cmd.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) return;

            var chunks = player.getInstance().getChunks();
            int count = 0;

            player.sendMessage(Component.text("", NamedTextColor.GOLD));
            player.sendMessage(Component.text("╔════════════════════════════════════════╗", NamedTextColor.GOLD));
            player.sendMessage(Component.text("║          LOADED CHUNKS (" +
                    chunks.size() + ")           ║", NamedTextColor.GOLD));
            player.sendMessage(Component.text("╠════════════════════════════════════════╣", NamedTextColor.GOLD));

            for (Chunk chunk : chunks) {
                if (count >= 20) {
                    player.sendMessage(Component.text("║  ... and " + (chunks.size() - 20) + " more chunks", NamedTextColor.GRAY));
                    break;
                }
                // TODO: Cache chunk-entity mappings to avoid O(chunks × entities) iteration
                int chunkEntityCount = 0;
                int cx = chunk.getChunkX();
                int cz = chunk.getChunkZ();
                for (Entity entity : player.getInstance().getEntities()) {
                    Pos entityPos = entity.getPosition();
                    if (entityPos.chunkX() == cx && entityPos.chunkZ() == cz) {
                        chunkEntityCount++;
                    }
                }
                player.sendMessage(Component.text("║  [" + cx + ", " + cz + "]", NamedTextColor.YELLOW)
                        .append(Component.text(" Entities: " + chunkEntityCount, NamedTextColor.WHITE)));
                count++;
            }

            player.sendMessage(Component.text("╚════════════════════════════════════════╝", NamedTextColor.GOLD));
            player.sendMessage(Component.text("", NamedTextColor.GOLD));
        });
        MinecraftServer.getCommandManager().register(cmd);
    }

    // ==================== Entity Commands ====================

    private static void registerEntityInfoCommand() {
        Command cmd = new Command("entityinfo");
        var radiusArg = ArgumentType.Integer("radius").min(1).max(500);

        cmd.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            int radius = context.get(radiusArg);

            int count = 0;
            int living = 0;
            int items = 0;
            Map<String, Integer> typeCounts = new HashMap<>();

            for (Entity entity : player.getInstance().getEntities()) {
                if (entity.getPosition().distance(player.getPosition()) <= radius) {
                    count++;
                    String typeName = entity.getEntityType().name();
                    typeCounts.merge(typeName, 1, Integer::sum);

                    if (entity instanceof net.minestom.server.entity.LivingEntity) {
                        living++;
                    }
                    if (entity.getEntityType() == EntityType.ITEM) {
                        items++;
                    }
                }
            }

            player.sendMessage(Component.text("", NamedTextColor.GOLD));
            player.sendMessage(Component.text("╔════════════════════════════════════════╗", NamedTextColor.GOLD));
            player.sendMessage(Component.text("║          ENTITY INFORMATION            ║", NamedTextColor.GOLD));
            player.sendMessage(Component.text("╠════════════════════════════════════════╣", NamedTextColor.GOLD));
            player.sendMessage(Component.text("║  Radius:      ", NamedTextColor.YELLOW)
                    .append(Component.text(radius + " blocks", NamedTextColor.WHITE)));
            player.sendMessage(Component.text("║  Total:       ", NamedTextColor.YELLOW)
                    .append(Component.text(count, NamedTextColor.WHITE)));
            player.sendMessage(Component.text("║  Living:      ", NamedTextColor.YELLOW)
                    .append(Component.text(living, NamedTextColor.WHITE)));
            player.sendMessage(Component.text("║  Items:       ", NamedTextColor.YELLOW)
                    .append(Component.text(items, NamedTextColor.WHITE)));
            player.sendMessage(Component.text("╠════════════════════════════════════════╣", NamedTextColor.GOLD));

            typeCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10)
                    .forEach(entry -> {
                        player.sendMessage(Component.text("║  " + entry.getKey() + ": ", NamedTextColor.YELLOW)
                                .append(Component.text(entry.getValue(), NamedTextColor.WHITE)));
                    });

            player.sendMessage(Component.text("╚════════════════════════════════════════╝", NamedTextColor.GOLD));
            player.sendMessage(Component.text("", NamedTextColor.GOLD));
        }, radiusArg);

        cmd.setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("Usage: /entityinfo <radius>", NamedTextColor.RED));
        });

        MinecraftServer.getCommandManager().register(cmd);
    }

    private static void registerEntityTypesCommand() {
        Command cmd = new Command("entitytypes");
        cmd.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) return;

            Map<String, Integer> typeCounts = new HashMap<>();
            for (Entity entity : player.getInstance().getEntities()) {
                String typeName = entity.getEntityType().name();
                typeCounts.merge(typeName, 1, Integer::sum);
            }

            player.sendMessage(Component.text("", NamedTextColor.GOLD));
            player.sendMessage(Component.text("╔════════════════════════════════════════╗", NamedTextColor.GOLD));
            player.sendMessage(Component.text("║         ENTITY TYPE COUNTS             ║", NamedTextColor.GOLD));
            player.sendMessage(Component.text("╠════════════════════════════════════════╣", NamedTextColor.GOLD));

            if (typeCounts.isEmpty()) {
                player.sendMessage(Component.text("║  No entities found!", NamedTextColor.GRAY));
            } else {
                typeCounts.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .forEach(entry -> {
                            player.sendMessage(Component.text("║  " + entry.getKey() + ": ", NamedTextColor.YELLOW)
                                    .append(Component.text(entry.getValue(), NamedTextColor.WHITE)));
                        });
            }

            player.sendMessage(Component.text("╚════════════════════════════════════════╝", NamedTextColor.GOLD));
            player.sendMessage(Component.text("", NamedTextColor.GOLD));
        });
        MinecraftServer.getCommandManager().register(cmd);
    }

    private static void registerClearEntitiesCommand() {
        Command cmd = new Command("clearentities");
        var typeArg = ArgumentType.Word("type");

        cmd.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            String typeName = context.get(typeArg).toUpperCase();

            int count = 0;
            for (Entity entity : player.getInstance().getEntities()) {
                if (entity.getEntityType().name().equalsIgnoreCase(typeName) &&
                        !(entity instanceof Player)) {
                    entity.remove();
                    count++;
                }
            }

            player.sendMessage(Component.text("✓ Removed " + count + " " + typeName + " entities!", NamedTextColor.GREEN));
            logger.info("Player {} cleared {} {} entities", player.getUsername(), count, typeName);
        }, typeArg);

        cmd.setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("Usage: /clearentities <type>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Example: /clearentities zombie", NamedTextColor.GRAY));
        });

        MinecraftServer.getCommandManager().register(cmd);
    }

    // ==================== Performance Commands ====================

    private static void registerLagCommand() {
        Command cmd = new Command("lag");
        cmd.setDefaultExecutor((sender, context) -> {
            double tps = monitor.getCurrentTps();
            double tickTime = monitor.getCurrentTickTime();
            MemoryStats mem = monitor.getMemoryStats();
            int onlinePlayers = MinecraftServer.getConnectionManager().getOnlinePlayers().size();

            int totalEntities = 0;
            for (var instance : MinecraftServer.getInstanceManager().getInstances()) {
                totalEntities += instance.getEntities().size();
            }

            NamedTextColor tpsColor = tps >= 18 ? NamedTextColor.GREEN : (tps >= 15 ? NamedTextColor.YELLOW : NamedTextColor.RED);
            NamedTextColor memColor = mem.heapUsed() < mem.heapMax() * 0.8 ? NamedTextColor.GREEN : NamedTextColor.RED;

            sender.sendMessage(Component.text("", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("╔════════════════════════════════════════╗", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("║         PERFORMANCE METRICS            ║", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("╠════════════════════════════════════════╣", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("║  TPS:         ", NamedTextColor.YELLOW)
                    .append(Component.text(String.format("%.2f", tps), tpsColor)));
            sender.sendMessage(Component.text("║  Tick Time:   ", NamedTextColor.YELLOW)
                    .append(Component.text(String.format("%.2f ms", tickTime), NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("║  Players:     ", NamedTextColor.YELLOW)
                    .append(Component.text(onlinePlayers, NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("║  Entities:    ", NamedTextColor.YELLOW)
                    .append(Component.text(totalEntities, NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("║  Memory:      ", NamedTextColor.YELLOW)
                    .append(Component.text(mem.heapUsed() / 1024 / 1024 + " MB", memColor)));
            sender.sendMessage(Component.text("║  Heap Usage:   ", NamedTextColor.YELLOW)
                    .append(Component.text(String.format("%.1f%%", (double) mem.heapUsed() / mem.heapMax() * 100), memColor)));
            sender.sendMessage(Component.text("╚════════════════════════════════════════╝", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("", NamedTextColor.GOLD));
        });
        MinecraftServer.getCommandManager().register(cmd);
    }

    private static void registerTimingsCommand() {
        Command cmd = new Command("timings");
        cmd.setDefaultExecutor((sender, context) -> {
            TickStats stats = monitor.getTickStats();

            sender.sendMessage(Component.text("", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("╔════════════════════════════════════════╗", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("║          TICK TIMINGS                  ║", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("╠════════════════════════════════════════╣", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("║  Samples:     ", NamedTextColor.YELLOW)
                    .append(Component.text(stats.sampleSize(), NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("║  Average:     ", NamedTextColor.YELLOW)
                    .append(Component.text(String.format("%.3f ms", stats.averageMs()),
                            stats.averageMs() <= 55 ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));
            sender.sendMessage(Component.text("║  Min:         ", NamedTextColor.YELLOW)
                    .append(Component.text(String.format("%.3f ms", stats.minMs()), NamedTextColor.GREEN)));
            sender.sendMessage(Component.text("║  Max:         ", NamedTextColor.YELLOW)
                    .append(Component.text(String.format("%.3f ms", stats.maxMs()), NamedTextColor.RED)));

            if (stats.p95Ms() > 0) {
                sender.sendMessage(Component.text("║  P95:         ", NamedTextColor.YELLOW)
                        .append(Component.text(String.format("%.3f ms", stats.p95Ms()), NamedTextColor.WHITE)));
            }
            if (stats.p99Ms() > 0) {
                sender.sendMessage(Component.text("║  P99:         ", NamedTextColor.YELLOW)
                        .append(Component.text(String.format("%.3f ms", stats.p99Ms()), NamedTextColor.WHITE)));
            }

            sender.sendMessage(Component.text("╚════════════════════════════════════════╝", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("", NamedTextColor.GOLD));
        });
        MinecraftServer.getCommandManager().register(cmd);
    }

    // ==================== Debug Panel (Boss Bar) ====================

    private static void registerDebugPanelCommand() {
        Command cmd = new Command("debugpanel");
        cmd.setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) return;

            UUID playerId = player.getUuid();
            boolean enabled = !panelEnabled.getOrDefault(playerId, false);
            panelEnabled.put(playerId, enabled);

            if (enabled) {
                createDebugPanel(player);
                player.sendMessage(Component.text("✓ Debug panel enabled!", NamedTextColor.GREEN));
            } else {
                removeDebugPanel(player);
                player.sendMessage(Component.text("✗ Debug panel disabled!", NamedTextColor.RED));
            }
        });
        MinecraftServer.getCommandManager().register(cmd);
    }

    private static void createDebugPanel(Player player) {
        BossBar bossBar = BossBar.bossBar(
                Component.text("Debug Panel").color(NamedTextColor.GOLD),
                1.0f,
                BossBar.Color.GREEN,
                BossBar.Overlay.PROGRESS
        );
        player.showBossBar(bossBar);
        debugPanels.put(player.getUuid(), bossBar);
    }

    private static void removeDebugPanel(Player player) {
        BossBar bossBar = debugPanels.remove(player.getUuid());
        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
    }

    private static void startDebugPanelTask() {
        if (panelTaskStarted) return;
        panelTaskStarted = true;

        MinecraftServer.getSchedulerManager().buildTask(() -> {
            double tps = monitor.getCurrentTps();
            MemoryStats mem = monitor.getMemoryStats();
            int onlinePlayers = MinecraftServer.getConnectionManager().getOnlinePlayers().size();

            int totalEntities = 0;
            for (var instance : MinecraftServer.getInstanceManager().getInstances()) {
                totalEntities += instance.getEntities().size();
            }

            long heapUsed = mem.heapUsed() / 1024 / 1024;
            long heapMax = mem.heapMax() / 1024 / 1024;

            // Update all active panels
            for (Map.Entry<UUID, BossBar> entry : debugPanels.entrySet()) {
                BossBar bossBar = entry.getValue();
                Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(entry.getKey());
                if (player == null || !panelEnabled.getOrDefault(entry.getKey(), false)) {
                    continue;
                }

                // Color based on TPS
                BossBar.Color color;
                if (tps >= 18) {
                    color = BossBar.Color.GREEN;
                } else if (tps >= 15) {
                    color = BossBar.Color.YELLOW;
                } else {
                    color = BossBar.Color.RED;
                }

                bossBar.color(color);
                bossBar.progress(Math.min(1.0f, (float) (tps / 20.0)));

                bossBar.name(Component.text("TPS: ", NamedTextColor.GOLD)
                        .append(Component.text(String.format("%.1f", tps), color == BossBar.Color.GREEN ? NamedTextColor.GREEN :
                                (color == BossBar.Color.YELLOW ? NamedTextColor.YELLOW : NamedTextColor.RED)))
                        .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("Mem: ", NamedTextColor.GOLD))
                        .append(Component.text(heapUsed + "/" + heapMax + "MB", NamedTextColor.AQUA))
                        .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("Players: ", NamedTextColor.GOLD))
                        .append(Component.text(String.valueOf(onlinePlayers), NamedTextColor.GREEN))
                        .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("Entities: ", NamedTextColor.GOLD))
                        .append(Component.text(String.valueOf(totalEntities), NamedTextColor.YELLOW)));
            }
        }).repeat(TaskSchedule.tick(20)).schedule(); // Update every second (20 ticks)
    }

    /**
     * Cleanup debug panel for a player
     */
    public static void removePlayer(UUID playerId) {
        panelEnabled.remove(playerId);
        BossBar bossBar = debugPanels.remove(playerId);
        if (bossBar != null) {
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerId);
            if (player != null) {
                player.hideBossBar(bossBar);
            }
        }
    }

    // ==================== NPC Commands ====================

    private static NPCManager npcManager;

    public static void setNPCManager(NPCManager manager) {
        npcManager = manager;
    }

    private static void registerNPCCommand() {
        Command cmd = new Command("npc");
        var actionArg = ArgumentType.Word("action").from("spawn", "remove", "list");
        var nameArg = ArgumentType.String("name");
        var idArg = ArgumentType.Integer("id").min(1);

        // /npc spawn <name>
        cmd.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can spawn NPCs", NamedTextColor.RED));
                return;
            }
            if (npcManager == null) {
                player.sendMessage(Component.text("NPC system is not initialized", NamedTextColor.RED));
                return;
            }
            String name = context.get(nameArg);
            Pos pos = player.getPosition().add(1, 0, 0);
            NPCManager.NPC npc = npcManager.spawnNPC(player.getInstance(), pos, name);
            player.sendMessage(Component.text("✓ Spawned NPC '", NamedTextColor.GREEN)
                    .append(Component.text(name, NamedTextColor.YELLOW))
                    .append(Component.text("' (ID: " + npc.id + ")", NamedTextColor.GREEN)));
            logger.info("Player {} spawned NPC '{}' (ID: {}) at {}", player.getUsername(), name, npc.id, pos);
        }, actionArg, nameArg);

        // /npc remove <id>
        cmd.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            if (npcManager == null) {
                player.sendMessage(Component.text("NPC system is not initialized", NamedTextColor.RED));
                return;
            }
            int id = context.get(idArg);
            if (npcManager.removeNPC(id)) {
                player.sendMessage(Component.text("✓ Removed NPC ID: " + id, NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("✗ NPC ID " + id + " not found", NamedTextColor.RED));
            }
        }, actionArg, idArg);

        // /npc list
        cmd.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            if (npcManager == null) {
                player.sendMessage(Component.text("NPC system is not initialized", NamedTextColor.RED));
                return;
            }
            var npcs = npcManager.getAllNPCs();
            if (npcs.isEmpty()) {
                player.sendMessage(Component.text("No NPCs spawned", NamedTextColor.YELLOW));
                return;
            }

            player.sendMessage(Component.text("", NamedTextColor.GOLD));
            player.sendMessage(Component.text("╔════════════════════════════════════════╗", NamedTextColor.GOLD));
            player.sendMessage(Component.text("║           NPC LIST (" + npcs.size() + ")              ║", NamedTextColor.GOLD));
            player.sendMessage(Component.text("╠════════════════════════════════════════╣", NamedTextColor.GOLD));

            for (NPCManager.NPC npc : npcs) {
                Pos pos = npc.getPosition();
                player.sendMessage(Component.text("║  ID: " + npc.id + " | Name: ", NamedTextColor.YELLOW)
                        .append(Component.text(npc.name, NamedTextColor.WHITE))
                        .append(Component.text(" | Pos: " + String.format("%.1f, %.1f, %.1f", pos.x(), pos.y(), pos.z()), NamedTextColor.GRAY)));
            }

            player.sendMessage(Component.text("╚════════════════════════════════════════╝", NamedTextColor.GOLD));
            player.sendMessage(Component.text("", NamedTextColor.GOLD));
        }, actionArg);

        cmd.setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("=== NPC Commands ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("/npc spawn <name>", NamedTextColor.YELLOW)
                    .append(Component.text(" - Spawn an NPC", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("/npc remove <id>", NamedTextColor.YELLOW)
                    .append(Component.text(" - Remove an NPC", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("/npc list", NamedTextColor.YELLOW)
                    .append(Component.text(" - List all NPCs", NamedTextColor.WHITE)));
        });

        MinecraftServer.getCommandManager().register(cmd);
    }
}
