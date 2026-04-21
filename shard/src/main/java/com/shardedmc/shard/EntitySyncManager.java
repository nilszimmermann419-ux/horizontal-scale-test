package com.shardedmc.shard;

import com.shardedmc.shared.RedisClient;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.EntityMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Synchronizes entity state across shards via Redis.
 * Each shard broadcasts entity updates and receives updates from other shards.
 */
public class EntitySyncManager {
    private static final Logger logger = LoggerFactory.getLogger(EntitySyncManager.class);
    
    private final RedisClient redis;
    private final String shardId;
    private final String channel;
    
    // Cache of external entities (from other shards)
    private final Map<UUID, EntityData> externalEntities = new ConcurrentHashMap<>();
    
    public EntitySyncManager(RedisClient redis, String shardId) {
        this.redis = redis;
        this.shardId = shardId;
        this.channel = "world:entity_sync";
        
        startListening();
    }
    
    /**
     * Broadcast an entity update to all other shards.
     */
    public void broadcastEntityUpdate(Entity entity) {
        if (entity instanceof Player) {
            return; // Players are handled separately
        }
        
        EntityData data = new EntityData(
                entity.getUuid().toString(),
                entity.getEntityType().name(),
                entity.getPosition().x(),
                entity.getPosition().y(),
                entity.getPosition().z(),
                entity.getVelocity().x(),
                entity.getVelocity().y(),
                entity.getVelocity().z(),
                entity instanceof LivingEntity ? ((LivingEntity) entity).getHealth() : 0,
                shardId
        );
        
        redis.publish(channel, data.toString());
    }
    
    /**
     * Broadcast entity removal.
     */
    public void broadcastEntityRemove(UUID entityUuid) {
        String message = "REMOVE:" + entityUuid.toString() + ":" + shardId;
        redis.publish(channel, message);
    }
    
    /**
     * Start listening for entity updates from other shards.
     */
    private void startListening() {
        redis.subscribe(channel, message -> {
            try {
                if (message.startsWith("REMOVE:")) {
                    handleEntityRemove(message);
                } else {
                    handleEntityUpdate(message);
                }
            } catch (Exception e) {
                logger.error("Failed to process entity sync message: {}", message, e);
            }
        });
    }
    
    private void handleEntityUpdate(String message) {
        EntityData data = EntityData.fromString(message);
        if (data == null || data.sourceShard.equals(shardId)) {
            return;
        }
        
        externalEntities.put(UUID.fromString(data.uuid), data);
        
        // In a full implementation, we would spawn/update the entity on this shard
        // For now, we just cache the data
        logger.debug("Received entity update from shard {}: {} at {}, {}, {}", 
                data.sourceShard, data.type, data.x, data.y, data.z);
    }
    
    private void handleEntityRemove(String message) {
        String[] parts = message.split(":");
        if (parts.length == 3) {
            String uuid = parts[1];
            String sourceShard = parts[2];
            
            if (!sourceShard.equals(shardId)) {
                externalEntities.remove(UUID.fromString(uuid));
                logger.debug("Entity {} removed by shard {}", uuid, sourceShard);
            }
        }
    }
    
    /**
     * Get cached external entity data.
     */
    public Map<UUID, EntityData> getExternalEntities() {
        return externalEntities;
    }
    
    /**
     * Simple data class for entity synchronization.
     */
    public static class EntityData {
        public final String uuid;
        public final String type;
        public final double x, y, z;
        public final double vx, vy, vz;
        public final double health;
        public final String sourceShard;
        
        public EntityData(String uuid, String type, double x, double y, double z,
                         double vx, double vy, double vz, double health, String sourceShard) {
            this.uuid = uuid;
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.health = health;
            this.sourceShard = sourceShard;
        }
        
        @Override
        public String toString() {
            return String.join(":", uuid, type, 
                    String.valueOf(x), String.valueOf(y), String.valueOf(z),
                    String.valueOf(vx), String.valueOf(vy), String.valueOf(vz),
                    String.valueOf(health), sourceShard);
        }
        
        public static EntityData fromString(String str) {
            String[] parts = str.split(":");
            if (parts.length != 9) {
                return null;
            }
            
            try {
                return new EntityData(
                        parts[0], parts[1],
                        Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), Double.parseDouble(parts[4]),
                        Double.parseDouble(parts[5]), Double.parseDouble(parts[6]), Double.parseDouble(parts[7]),
                        Double.parseDouble(parts[8]), parts[9]
                );
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
