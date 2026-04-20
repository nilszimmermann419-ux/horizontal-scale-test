package com.shardedmc.shard.vanilla;

import net.kyori.adventure.text.Component;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CraftingManager {
    private static final Logger logger = LoggerFactory.getLogger(CraftingManager.class);
    
    public void register(GlobalEventHandler eventHandler) {
        eventHandler.addListener(PlayerBlockInteractEvent.class, this::onBlockInteract);
    }
    
    private void onBlockInteract(PlayerBlockInteractEvent event) {
        Block block = event.getBlock();
        
        if (block.compare(Block.CRAFTING_TABLE)) {
            event.getPlayer().sendMessage(Component.text("Crafting table opened!"));
        }
    }
}
