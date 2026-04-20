package com.shardedmc.shard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Debug commands for testing and development.
 */
public class DebugCommands {
    private static final Logger logger = LoggerFactory.getLogger(DebugCommands.class);
    
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
        
        logger.info("Debug commands registered");
    }
    
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
}
