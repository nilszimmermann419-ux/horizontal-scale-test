package com.shardedmc.shard.vanilla;

import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.ai.goal.RandomStrollGoal;
import net.minestom.server.entity.ai.target.ClosestEntityTarget;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.instance.Instance;
import net.minestom.server.coordinate.Pos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EntityAI {
    private static final Logger logger = LoggerFactory.getLogger(EntityAI.class);
    private final Random random = new Random();
    private final Map<UUID, EntityBehavior> behaviors = new ConcurrentHashMap<>();
    
    public void register(GlobalEventHandler eventHandler) {
        // Register spawn handlers for each mob type
    }
    
    public void tick() {
        for (EntityBehavior behavior : behaviors.values()) {
            behavior.tick();
        }
    }
    
    public void spawnMob(Instance instance, Pos pos, EntityType type) {
        EntityCreature entity = new EntityCreature(type);
        entity.setInstance(instance, pos);
        
        setupAI(entity, type);
        
        behaviors.put(entity.getUuid(), new EntityBehavior(entity));
    }
    
    private void setupAI(EntityCreature entity, EntityType type) {
        var goalSelectors = new java.util.ArrayList<net.minestom.server.entity.ai.GoalSelector>();
        var targetSelectors = new java.util.ArrayList<net.minestom.server.entity.ai.TargetSelector>();
        
        String typeName = type.name().toLowerCase();
        switch (typeName) {
            case "zombie":
            case "skeleton":
            case "spider":
                goalSelectors.add(new RandomStrollGoal(entity, 20));
                targetSelectors.add(new ClosestEntityTarget(entity, 16.0, 
                    e -> e.getEntityType() == EntityType.PLAYER));
                break;
                
            case "cow":
            case "pig":
            case "sheep":
            case "chicken":
                goalSelectors.add(new RandomStrollGoal(entity, 20));
                break;
                
            case "villager":
                goalSelectors.add(new RandomStrollGoal(entity, 10));
                break;
        }
        
        entity.addAIGroup(goalSelectors, targetSelectors);
    }
    
    private static class EntityBehavior {
        private final EntityCreature entity;
        
        EntityBehavior(EntityCreature entity) {
            this.entity = entity;
        }
        
        void tick() {
            // Custom behavior logic
            // For now, let Minestom's AI system handle it
        }
    }
}
