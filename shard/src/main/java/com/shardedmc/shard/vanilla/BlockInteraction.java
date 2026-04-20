package com.shardedmc.shard.vanilla;

import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class BlockInteraction {
    private static final Logger logger = LoggerFactory.getLogger(BlockInteraction.class);
    
    private static final Map<String, Float> BLOCK_HARDNESS = new HashMap<>();
    static {
        BLOCK_HARDNESS.put("minecraft:stone", 1.5f);
        BLOCK_HARDNESS.put("minecraft:dirt", 0.5f);
        BLOCK_HARDNESS.put("minecraft:grass_block", 0.6f);
        BLOCK_HARDNESS.put("minecraft:oak_log", 2.0f);
        BLOCK_HARDNESS.put("minecraft:spruce_log", 2.0f);
        BLOCK_HARDNESS.put("minecraft:birch_log", 2.0f);
        BLOCK_HARDNESS.put("minecraft:oak_planks", 2.0f);
        BLOCK_HARDNESS.put("minecraft:bedrock", -1.0f);
        BLOCK_HARDNESS.put("minecraft:iron_ore", 3.0f);
        BLOCK_HARDNESS.put("minecraft:coal_ore", 3.0f);
        BLOCK_HARDNESS.put("minecraft:diamond_ore", 3.0f);
        BLOCK_HARDNESS.put("minecraft:gold_ore", 3.0f);
        BLOCK_HARDNESS.put("minecraft:sand", 0.5f);
        BLOCK_HARDNESS.put("minecraft:gravel", 0.6f);
        BLOCK_HARDNESS.put("minecraft:cobblestone", 2.0f);
        BLOCK_HARDNESS.put("minecraft:glass", 0.3f);
    }
    
    public void register(GlobalEventHandler eventHandler) {
        eventHandler.addListener(PlayerBlockBreakEvent.class, this::onBlockBreak);
        eventHandler.addListener(PlayerBlockPlaceEvent.class, this::onBlockPlace);
        eventHandler.addListener(PlayerBlockInteractEvent.class, this::onBlockInteract);
    }
    
    private void onBlockBreak(PlayerBlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        if (!canBreakBlock(player, block)) {
            event.setCancelled(true);
            return;
        }
        
        damageTool(player);
    }
    
    private void onBlockPlace(PlayerBlockPlaceEvent event) {
        // Block placing handled by Minestom
    }
    
    private void onBlockInteract(PlayerBlockInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        if (block.name().contains("door") || block.name().contains("trapdoor") || block.name().contains("fence_gate")) {
            toggleDoor(block, player);
        }
        
        if (block.name().contains("chest") && !block.name().contains("ender")) {
            player.sendMessage(Component.text("Chest opened!"));
        }
        
        if (block.name().contains("furnace") || block.name().contains("smoker") || block.name().contains("blast_furnace")) {
            player.sendMessage(Component.text("Furnace opened!"));
        }
        
        if (block.name().contains("bed") && !block.name().contains("rock")) {
            player.setRespawnPoint(player.getPosition());
            player.sendMessage(Component.text("Spawn point set!"));
        }
    }
    
    private boolean canBreakBlock(Player player, Block block) {
        if (block == Block.BEDROCK) {
            return false;
        }
        return true;
    }
    
    private void damageTool(Player player) {
        ItemStack held = player.getItemInMainHand();
        Material mat = held.material();
        
        if (isTool(mat)) {
            // Tool durability not implemented in basic version
        }
    }
    
    private boolean isTool(Material mat) {
        String name = mat.name().toLowerCase();
        return name.contains("pickaxe") || name.contains("axe") || 
               name.contains("shovel") || name.contains("hoe");
    }
    
    private void toggleDoor(Block block, Player player) {
        logger.debug("Player {} toggled door {}", player.getUsername(), block.name());
    }
}
