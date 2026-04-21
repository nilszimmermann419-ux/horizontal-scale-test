package com.shardedmc.shard;

import com.shardedmc.shard.vanilla.*;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.GlobalEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VanillaMechanics {
    private static final Logger logger = LoggerFactory.getLogger(VanillaMechanics.class);
    private static final long TICK_MS = 50;
    
    private final RedstoneEngine redstone;
    private final EntityAI entityAI;
    private final CraftingManager crafting;
    private final BlockInteraction blockInteraction;
    private final CombatSystem combat;
    private final FarmingSystem farming;
    
    public VanillaMechanics() {
        this.redstone = new RedstoneEngine();
        this.entityAI = new EntityAI();
        this.crafting = new CraftingManager();
        this.blockInteraction = new BlockInteraction();
        this.combat = new CombatSystem();
        this.farming = new FarmingSystem();
    }
    
    public void register(GlobalEventHandler eventHandler) {
        redstone.register(eventHandler);
        entityAI.register(eventHandler);
        crafting.register(eventHandler);
        blockInteraction.register(eventHandler);
        combat.register(eventHandler);
        farming.register(eventHandler);
        
        MinecraftServer.getSchedulerManager().buildTask(this::tick)
            .repeat(java.time.Duration.ofMillis(TICK_MS))
            .schedule();
        
        logger.info("Vanilla mechanics registered (6 subsystems)");
    }
    
    private void tick() {
        redstone.tick();
        entityAI.tick();
        farming.tick();
    }
}
