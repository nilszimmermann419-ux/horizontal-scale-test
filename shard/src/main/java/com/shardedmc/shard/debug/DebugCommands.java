package com.shardedmc.shard.debug;

import com.shardedmc.shard.ShardServer;
import com.shardedmc.shard.region.RegionManager;
import com.shardedmc.shard.world.WorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class DebugCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(DebugCommands.class);

    private final String shardId;
    private final RegionManager regionManager;
    private final WorldManager worldManager;

    public DebugCommands(String shardId, RegionManager regionManager, WorldManager worldManager) {
        this.shardId = shardId;
        this.regionManager = regionManager;
        this.worldManager = worldManager;
    }

    public void register() {
        MinecraftServer.getCommandManager().register(createShardInfoCommand());
        MinecraftServer.getCommandManager().register(createShardRegionsCommand());
        MinecraftServer.getCommandManager().register(createShardPlayersCommand());
        MinecraftServer.getCommandManager().register(createShardTpsCommand());
        MinecraftServer.getCommandManager().register(createShardTransferCommand());
        LOGGER.info("Debug commands registered");
    }

    private Command createShardInfoCommand() {
        Command command = new Command("shardinfo");
        command.setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("=== Shard Info ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Shard ID: ", NamedTextColor.GRAY)
                    .append(Component.text(shardId, NamedTextColor.GREEN)));
            sender.sendMessage(Component.text("Region Count: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(regionManager.getOwnedRegions().size()), NamedTextColor.GREEN)));
            sender.sendMessage(Component.text("Player Count: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(worldManager.getPlayerCount()), NamedTextColor.GREEN)));
            sender.sendMessage(Component.text("Max Players: ", NamedTextColor.GRAY)
                    .append(Component.text("2000", NamedTextColor.GREEN)));
        });
        return command;
    }

    private Command createShardRegionsCommand() {
        Command command = new Command("shardregions");
        command.setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("=== Owned Regions ===", NamedTextColor.GOLD));
            var regions = regionManager.getOwnedRegions();
            if (regions.isEmpty()) {
                sender.sendMessage(Component.text("No regions owned", NamedTextColor.GRAY));
            } else {
                for (RegionManager.RegionCoord region : regions) {
                    sender.sendMessage(Component.text("Region: ", NamedTextColor.GRAY)
                            .append(Component.text(region.toString(), NamedTextColor.GREEN)));
                }
            }
        });
        return command;
    }

    private Command createShardPlayersCommand() {
        Command command = new Command("shardplayers");
        command.setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("=== Online Players ===", NamedTextColor.GOLD));
            Instance instance = worldManager.getInstance();
            if (instance != null) {
                Collection<Player> players = instance.getPlayers();
                if (players.isEmpty()) {
                    sender.sendMessage(Component.text("No players online", NamedTextColor.GRAY));
                } else {
                    sender.sendMessage(Component.text("Total: " + players.size(), NamedTextColor.YELLOW));
                    for (Player player : players) {
                        sender.sendMessage(Component.text("- ", NamedTextColor.GRAY)
                                .append(Component.text(player.getUsername(), NamedTextColor.GREEN)));
                    }
                }
            } else {
                sender.sendMessage(Component.text("No instance available", NamedTextColor.RED));
            }
        });
        return command;
    }

    private Command createShardTpsCommand() {
        Command command = new Command("shardtps");
        command.setDefaultExecutor((sender, context) -> {
            sender.sendMessage(Component.text("=== Tick Rate ===", NamedTextColor.GOLD));
            // Minestom doesn't expose TPS directly, so we show a placeholder
            sender.sendMessage(Component.text("Current TPS: ", NamedTextColor.GRAY)
                    .append(Component.text("20.0", NamedTextColor.GREEN)));
            sender.sendMessage(Component.text("Target TPS: ", NamedTextColor.GRAY)
                    .append(Component.text("20.0", NamedTextColor.GREEN)));
        });
        return command;
    }

    private Command createShardTransferCommand() {
        Command command = new Command("shardtransfer");
        ArgumentString playerArg = ArgumentType.String("player");
        ArgumentString targetShardArg = ArgumentType.String("targetShard");

        command.addSyntax((sender, context) -> {
            String playerName = context.get(playerArg);
            String targetShard = context.get(targetShardArg);

            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(playerName);
            if (player == null) {
                sender.sendMessage(Component.text("Player not found: " + playerName, NamedTextColor.RED));
                return;
            }

            sender.sendMessage(Component.text("Initiating transfer of ", NamedTextColor.GRAY)
                    .append(Component.text(playerName, NamedTextColor.GREEN))
                    .append(Component.text(" to shard ", NamedTextColor.GRAY))
                    .append(Component.text(targetShard, NamedTextColor.GREEN)));

            // TODO: Implement actual player transfer logic
            LOGGER.info("Admin {} requested transfer of {} to shard {}",
                    sender instanceof Player ? ((Player) sender).getUsername() : "console",
                    playerName, targetShard);

            sender.sendMessage(Component.text("Transfer request sent (not yet implemented)", NamedTextColor.YELLOW));
        }, playerArg, targetShardArg);

        return command;
    }
}
